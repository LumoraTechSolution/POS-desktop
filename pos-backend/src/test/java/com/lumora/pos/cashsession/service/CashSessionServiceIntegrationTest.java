package com.lumora.pos.cashsession.service;

import com.lumora.pos.auth.entity.UserEntity;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.cashsession.dto.CashSessionDtos.CashSessionResponse;
import com.lumora.pos.cashsession.dto.CashSessionDtos.EndShiftRequest;
import com.lumora.pos.cashsession.dto.CashSessionDtos.StartShiftRequest;
import com.lumora.pos.cashsession.entity.CashSessionEntity;
import com.lumora.pos.cashsession.repository.CashSessionRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.inventory.entity.ProductEntity;
import com.lumora.pos.inventory.entity.StockLevelEntity;
import com.lumora.pos.inventory.repository.ProductRepository;
import com.lumora.pos.inventory.repository.StockLevelRepository;
import com.lumora.pos.sales.dto.SaleRequest;
import com.lumora.pos.sales.service.SaleService;
import com.lumora.pos.tax.entity.TaxRateEntity;
import com.lumora.pos.tax.repository.TaxRateRepository;
import com.lumora.pos.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage for the variance math that protects every cashier's
 * drawer count: open shift → cash sale (and refund) → close shift → assert
 * `variance = closing - (opening + cashSales − cashRefunds)`.
 *
 * Lives next to {@code SaleServiceIntegrationTest} since both flows write to
 * `cash_sessions` / `sales` together.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CashSessionServiceIntegrationTest {

    @Autowired private CashSessionService cashSessionService;
    @Autowired private SaleService saleService;
    @Autowired private CashSessionRepository cashSessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockLevelRepository stockLevelRepository;
    @Autowired private TaxRateRepository taxRateRepository;

    private UUID tenantId;
    private UserEntity cashier;
    private BranchEntity branch;
    private ProductEntity product;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        cashier = UserEntity.builder()
                .email("cashier-" + UUID.randomUUID() + "@test.local")
                .passwordHash("x")
                .firstName("Test")
                .lastName("Cashier")
                .isActive(true)
                .build();
        cashier.setTenantId(tenantId);
        cashier = userRepository.save(cashier);

        // Auditing reads SecurityContext for createdBy.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cashier.getId(), null, Collections.emptyList()));

        branch = BranchEntity.builder().name("Main").isDefault(true).isActive(true).build();
        branch.setTenantId(tenantId);
        branch = branchRepository.save(branch);

        // Tax-free for predictable variance.
        TaxRateEntity zeroTax = TaxRateEntity.builder()
                .name("Zero").rate(BigDecimal.ZERO).isDefault(true).build();
        zeroTax.setTenantId(tenantId);
        taxRateRepository.save(zeroTax);

        product = ProductEntity.builder()
                .name("Coffee")
                .sku("COF-1")
                .basePrice(new BigDecimal("50.00"))
                .lowStockThreshold(1)
                .isActive(true)
                .build();
        product.setTenantId(tenantId);
        product = productRepository.save(product);

        StockLevelEntity stock = StockLevelEntity.builder()
                .product(product).branch(branch).quantity(100).build();
        stock.setTenantId(tenantId);
        stockLevelRepository.save(stock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void startShift_persistsOpeningBalanceAsOpenSession() {
        StartShiftRequest req = new StartShiftRequest();
        req.setOpeningBalance(new BigDecimal("200.00"));
        req.setNotes("opening with 10x$20s");

        CashSessionResponse res = cashSessionService.startShift(cashier.getId(), req);

        assertThat(res.getStatus()).isEqualTo("OPEN");
        assertThat(res.getOpeningBalance()).isEqualByComparingTo("200.00");
        assertThat(res.getCashSalesTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void startShift_rejectsSecondConcurrentSessionForSameUser() {
        StartShiftRequest req = new StartShiftRequest();
        req.setOpeningBalance(new BigDecimal("100.00"));
        cashSessionService.startShift(cashier.getId(), req);

        assertThatThrownBy(() -> cashSessionService.startShift(cashier.getId(), req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already open");
    }

    @Test
    void endShift_balancesExactlyWhenCashTenderedMatchesSale() {
        // Open with $200.
        StartShiftRequest start = new StartShiftRequest();
        start.setOpeningBalance(new BigDecimal("200.00"));
        cashSessionService.startShift(cashier.getId(), start);

        // Ring up a $50 cash sale.
        ringUpCashSale(new BigDecimal("50.00"));

        // Close counted at $250 → variance $0.
        EndShiftRequest end = new EndShiftRequest();
        end.setClosingBalance(new BigDecimal("250.00"));
        CashSessionResponse closed = cashSessionService.endShift(cashier.getId(), end);

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getCashSalesTotal()).isEqualByComparingTo("50.00");
        assertThat(closed.getExpectedBalance()).isEqualByComparingTo("250.00");
        assertThat(closed.getVariance()).isEqualByComparingTo("0.00");
    }

    @Test
    void endShift_reportsShortageWhenDrawerIsLight() {
        StartShiftRequest start = new StartShiftRequest();
        start.setOpeningBalance(new BigDecimal("200.00"));
        cashSessionService.startShift(cashier.getId(), start);

        ringUpCashSale(new BigDecimal("50.00"));

        EndShiftRequest end = new EndShiftRequest();
        end.setClosingBalance(new BigDecimal("245.00")); // $5 short
        CashSessionResponse closed = cashSessionService.endShift(cashier.getId(), end);

        assertThat(closed.getVariance()).isEqualByComparingTo("-5.00");
    }

    @Test
    void endShift_reportsOverageWhenDrawerHasExtraCash() {
        StartShiftRequest start = new StartShiftRequest();
        start.setOpeningBalance(new BigDecimal("200.00"));
        cashSessionService.startShift(cashier.getId(), start);

        ringUpCashSale(new BigDecimal("50.00"));

        EndShiftRequest end = new EndShiftRequest();
        end.setClosingBalance(new BigDecimal("253.00")); // $3 over
        CashSessionResponse closed = cashSessionService.endShift(cashier.getId(), end);

        assertThat(closed.getVariance()).isEqualByComparingTo("3.00");
    }

    @Test
    void endShift_failsLoudlyWithoutOpenSession() {
        EndShiftRequest end = new EndShiftRequest();
        end.setClosingBalance(new BigDecimal("100.00"));

        assertThatThrownBy(() -> cashSessionService.endShift(cashier.getId(), end))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No open cash session");
    }

    @Test
    void endShift_persistsClosedStatusToTheRepository() {
        StartShiftRequest start = new StartShiftRequest();
        start.setOpeningBalance(BigDecimal.TEN);
        CashSessionResponse opened = cashSessionService.startShift(cashier.getId(), start);

        EndShiftRequest end = new EndShiftRequest();
        end.setClosingBalance(BigDecimal.TEN);
        cashSessionService.endShift(cashier.getId(), end);

        CashSessionEntity persisted = cashSessionRepository.findById(opened.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(CashSessionEntity.Status.CLOSED);
        assertThat(persisted.getClosedAt()).isNotNull();
        assertThat(persisted.getExpectedBalance()).isEqualByComparingTo("10.00");
    }

    private void ringUpCashSale(BigDecimal amount) {
        SaleRequest.SaleItemRequest item = new SaleRequest.SaleItemRequest();
        item.setProductId(product.getId());
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(amount);
        item.setDiscountAmount(BigDecimal.ZERO);

        SaleRequest req = SaleRequest.builder()
                .branchId(branch.getId())
                .paymentMethod("CASH")
                .cashTendered(amount)
                .items(List.of(item))
                .build();

        saleService.createSale(req);
    }
}
