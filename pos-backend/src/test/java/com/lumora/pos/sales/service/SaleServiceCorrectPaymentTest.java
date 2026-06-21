package com.lumora.pos.sales.service;

import com.lumora.pos.TestUtils;
import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.audit.service.AuditService;
import com.lumora.pos.auth.entity.RoleEntity;
import com.lumora.pos.auth.entity.UserEntity;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.branch.service.BranchAccessGuard;
import com.lumora.pos.cashsession.entity.CashSessionEntity;
import com.lumora.pos.cashsession.repository.CashSessionRepository;
import com.lumora.pos.cashsession.service.CashSessionService;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.customer.repository.CustomerRepository;
import com.lumora.pos.inventory.repository.ProductRepository;
import com.lumora.pos.inventory.repository.StockLevelRepository;
import com.lumora.pos.sales.dto.PaymentCorrectionRequest;
import com.lumora.pos.sales.dto.SaleResponse;
import com.lumora.pos.sales.entity.SaleEntity;
import com.lumora.pos.sales.repository.SaleRepository;
import com.lumora.pos.tax.service.TaxRateService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SaleService#correctPayment} — the post-completion
 * payment-metadata correction path. Covers the cashier self-serve window,
 * manager-PIN escalation, closed-session rejection, and CASH/CARD/SPLIT
 * tender-derivation rules.
 *
 * <p>All dependencies are mocked; no DB.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SaleService.correctPayment")
class SaleServiceCorrectPaymentTest {

    @Mock private SaleRepository saleRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private BranchAccessGuard branchAccessGuard;
    @Mock private StockLevelRepository stockLevelRepository;
    @Mock private AuditService auditService;
    @Mock private TaxRateService taxRateService;
    @Mock private CashSessionService cashSessionService;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SaleService saleService;

    private UUID saleId;
    private UUID sessionId;
    private SaleEntity sale;
    private CashSessionEntity openSession;

    @BeforeEach
    void setUp() {
        TestUtils.setupDefaultContext();
        saleId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        sale = new SaleEntity();
        sale.setId(saleId);
        sale.setTenantId(TestUtils.TEST_TENANT_ID);
        sale.setInvoiceNumber("INV-TEST");
        sale.setTotalAmount(new BigDecimal("800.00"));
        sale.setTaxAmount(BigDecimal.ZERO);
        sale.setDiscountAmount(BigDecimal.ZERO);
        sale.setNetAmount(new BigDecimal("800.00"));
        sale.setCashTendered(new BigDecimal("800.00"));
        sale.setPaymentMethod(SaleEntity.PaymentMethod.CASH);
        sale.setPaymentStatus(SaleEntity.PaymentStatus.PAID);
        sale.setCashSessionId(sessionId);
        sale.setCreatedBy(TestUtils.TEST_USER_ID);
        sale.setCreatedAt(LocalDateTime.now().minusMinutes(1));

        openSession = new CashSessionEntity();
        openSession.setId(sessionId);
        openSession.setTenantId(TestUtils.TEST_TENANT_ID);
        openSession.setUserId(TestUtils.TEST_USER_ID);
        openSession.setStatus(CashSessionEntity.Status.OPEN);

        lenient().when(saleRepository.findByIdAndTenantId(saleId, TestUtils.TEST_TENANT_ID))
                .thenReturn(Optional.of(sale));
        lenient().when(cashSessionRepository.findByIdAndTenantId(sessionId, TestUtils.TEST_TENANT_ID))
                .thenReturn(Optional.of(openSession));
        lenient().when(saleRepository.save(any(SaleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // mapToResponse looks up the cashier via userRepository
        lenient().when(userRepository.findById(any(UUID.class)))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TestUtils.clearContext();
    }

    private PaymentCorrectionRequest req(String method, BigDecimal tender, String pin) {
        return PaymentCorrectionRequest.builder()
                .paymentMethod(method)
                .cashTendered(tender)
                .managerPin(pin)
                .build();
    }

    private UserEntity managerWithPin(String hashedPin) {
        UserEntity u = UserEntity.builder()
                .email("mgr@test")
                .passwordHash("x")
                .firstName("M")
                .lastName("Anager")
                .pin(hashedPin)
                .build();
        u.setId(UUID.randomUUID());
        u.setTenantId(TestUtils.TEST_TENANT_ID);
        RoleEntity managerRole = RoleEntity.builder().name("MANAGER").build();
        Set<RoleEntity> roles = new HashSet<>();
        roles.add(managerRole);
        u.setRoles(roles);
        return u;
    }

    // ---------------------------------------------------------------
    // Self-serve window
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Cashier may correct their own sale within the self-serve window without a PIN")
    void cashierWithinWindow_correctsTender() {
        sale.setPaymentMethod(SaleEntity.PaymentMethod.SPLIT);
        sale.setCashTendered(new BigDecimal("500.00"));

        SaleResponse after = saleService.correctPayment(saleId,
                req(null, new BigDecimal("300.00"), null));

        assertThat(sale.getCashTendered()).isEqualByComparingTo("300.00");
        assertThat(after.getPaymentMethod()).isEqualTo("SPLIT");
        verify(auditService).log(eq(AuditAction.SALE_PAYMENT_CORRECT), eq("SALE"),
                eq(saleId), any(SaleResponse.class), any(SaleResponse.class));
    }

    @Test
    @DisplayName("Cashier outside the self-serve window without a PIN is rejected")
    void cashierExpiredWindow_noPin_rejected() {
        sale.setCreatedAt(LocalDateTime.now().minusMinutes(30));

        assertThatThrownBy(() ->
                saleService.correctPayment(saleId, req("CARD", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Manager PIN is required");

        verify(saleRepository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cashier outside the window with a valid manager PIN proceeds")
    void cashierExpiredWindow_validManagerPin_ok() {
        sale.setCreatedAt(LocalDateTime.now().minusMinutes(30));

        UserEntity mgr = managerWithPin("hashed-mgr-pin");
        when(userRepository.findActiveUsersWithPinByTenantId(TestUtils.TEST_TENANT_ID))
                .thenReturn(List.of(mgr));
        when(passwordEncoder.matches("9999", "hashed-mgr-pin")).thenReturn(true);

        saleService.correctPayment(saleId, req("CARD", null, "9999"));

        assertThat(sale.getPaymentMethod()).isEqualTo(SaleEntity.PaymentMethod.CARD);
        assertThat(sale.getCashTendered()).isEqualByComparingTo("0");
        verify(auditService).log(eq(AuditAction.SALE_PAYMENT_CORRECT), any(), eq(saleId),
                any(), any());
    }

    @Test
    @DisplayName("PIN that matches a non-manager user is rejected")
    void nonManagerPin_rejected() {
        sale.setCreatedAt(LocalDateTime.now().minusMinutes(30));

        UserEntity cashier = UserEntity.builder()
                .email("c@test").passwordHash("x").firstName("C").lastName("Ash")
                .pin("hashed-cashier-pin")
                .build();
        cashier.setId(UUID.randomUUID());
        cashier.setTenantId(TestUtils.TEST_TENANT_ID);
        Set<RoleEntity> roles = new HashSet<>();
        roles.add(RoleEntity.builder().name("CASHIER").build());
        cashier.setRoles(roles);

        when(userRepository.findActiveUsersWithPinByTenantId(TestUtils.TEST_TENANT_ID))
                .thenReturn(List.of(cashier));
        when(passwordEncoder.matches("1234", "hashed-cashier-pin")).thenReturn(true);

        assertThatThrownBy(() ->
                saleService.correctPayment(saleId, req("CARD", null, "1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid manager PIN");
    }

    @Test
    @DisplayName("Different cashier inside the window still needs a manager PIN")
    void differentCashier_needsManagerPin() {
        sale.setCreatedBy(UUID.randomUUID());

        assertThatThrownBy(() ->
                saleService.correctPayment(saleId, req("CARD", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Manager PIN is required");
    }

    // ---------------------------------------------------------------
    // Status / session preconditions
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Already-refunded sale cannot be corrected")
    void refundedSale_rejected() {
        sale.setPaymentStatus(SaleEntity.PaymentStatus.REFUNDED);

        assertThatThrownBy(() ->
                saleService.correctPayment(saleId, req("CARD", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only PAID sales");
    }

    @Test
    @DisplayName("Sale from a closed shift cannot be corrected")
    void closedSession_rejected() {
        openSession.setStatus(CashSessionEntity.Status.CLOSED);

        assertThatThrownBy(() ->
                saleService.correctPayment(saleId, req("CARD", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shift this sale belongs to has been closed");
    }

    @Test
    @DisplayName("Sale not attached to any cash session cannot be corrected")
    void noSession_rejected() {
        sale.setCashSessionId(null);

        assertThatThrownBy(() ->
                saleService.correctPayment(saleId, req("CARD", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not attached to a cash session");
    }

    @Test
    @DisplayName("Empty request (no method, no tender) is rejected")
    void emptyRequest_rejected() {
        assertThatThrownBy(() ->
                saleService.correctPayment(saleId, req(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Provide paymentMethod");
    }

    // ---------------------------------------------------------------
    // Method / tender derivation rules
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Changing CASH → CARD zeroes cash_tendered")
    void cashToCard_zeroesTender() {
        saleService.correctPayment(saleId, req("CARD", null, null));

        assertThat(sale.getPaymentMethod()).isEqualTo(SaleEntity.PaymentMethod.CARD);
        assertThat(sale.getCashTendered()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Changing CARD → CASH sets cash_tendered to netAmount")
    void cardToCash_setsTenderToNet() {
        sale.setPaymentMethod(SaleEntity.PaymentMethod.CARD);
        sale.setCashTendered(BigDecimal.ZERO);

        saleService.correctPayment(saleId, req("CASH", null, null));

        assertThat(sale.getPaymentMethod()).isEqualTo(SaleEntity.PaymentMethod.CASH);
        assertThat(sale.getCashTendered()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("CASH tender below netAmount is rejected")
    void cashTenderBelowNet_rejected() {
        assertThatThrownBy(() ->
                saleService.correctPayment(saleId,
                        req(null, new BigDecimal("100.00"), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CASH tender must be at least");
    }

    @Test
    @DisplayName("Unknown payment method string is rejected")
    void unknownMethod_rejected() {
        assertThatThrownBy(() ->
                saleService.correctPayment(saleId, req("CRYPTO", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown payment method");
    }

    @Test
    @DisplayName("SPLIT cash portion above netAmount is rejected")
    void splitTenderOverNet_rejected() {
        sale.setPaymentMethod(SaleEntity.PaymentMethod.SPLIT);

        assertThatThrownBy(() ->
                saleService.correctPayment(saleId,
                        req(null, new BigDecimal("999.00"), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SPLIT cash portion");
    }

    // ---------------------------------------------------------------
    // Gross tendered / change preserved for the receipt
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Correcting a CASH tender keeps cash_tendered at net but records the gross + change")
    void cashTenderCorrection_recordsGrossAndChange() {
        // Customer actually handed over 1000 for an 800 sale → 200 change.
        SaleResponse after = saleService.correctPayment(saleId,
                req(null, new BigDecimal("1000.00"), null));

        // Drawer math still uses net; the gross/change ride on the receipt fields.
        assertThat(sale.getCashTendered()).isEqualByComparingTo("800.00");
        assertThat(sale.getAmountTendered()).isEqualByComparingTo("1000.00");
        assertThat(after.getAmountTendered()).isEqualByComparingTo("1000.00");
        assertThat(after.getChangeDue()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Changing CARD → CASH with an explicit gross records tender + change")
    void cardToCash_withGross_recordsTenderAndChange() {
        // The customer paid by card but actually handed over 1500 cash for an
        // 800 sale — the correction must capture that, not assume an exact tender.
        sale.setPaymentMethod(SaleEntity.PaymentMethod.CARD);
        sale.setCashTendered(BigDecimal.ZERO);
        sale.setAmountTendered(null);

        SaleResponse after = saleService.correctPayment(saleId,
                req("CASH", new BigDecimal("1500.00"), null));

        assertThat(sale.getPaymentMethod()).isEqualTo(SaleEntity.PaymentMethod.CASH);
        assertThat(sale.getCashTendered()).isEqualByComparingTo("800.00");   // drawer math
        assertThat(sale.getAmountTendered()).isEqualByComparingTo("1500.00"); // receipt
        assertThat(after.getChangeDue()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("Changing CASH → CARD clears the recorded gross tender")
    void cashToCard_clearsAmountTendered() {
        sale.setAmountTendered(new BigDecimal("1000.00"));

        SaleResponse after = saleService.correctPayment(saleId, req("CARD", null, null));

        assertThat(sale.getAmountTendered()).isNull();
        assertThat(after.getChangeDue()).isEqualByComparingTo("0");
    }
}
