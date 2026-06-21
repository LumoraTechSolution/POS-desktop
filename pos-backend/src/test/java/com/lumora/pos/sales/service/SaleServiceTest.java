package com.lumora.pos.sales.service;

import com.lumora.pos.TestUtils;
import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.audit.service.AuditService;
import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.branch.service.BranchAccessGuard;
import com.lumora.pos.cashsession.entity.CashSessionEntity;
import com.lumora.pos.cashsession.repository.CashSessionRepository;
import com.lumora.pos.cashsession.service.CashSessionService;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.customer.entity.CustomerEntity;
import com.lumora.pos.customer.repository.CustomerRepository;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.inventory.entity.ProductEntity;
import com.lumora.pos.inventory.entity.StockLevelEntity;
import com.lumora.pos.inventory.repository.ProductRepository;
import com.lumora.pos.inventory.repository.StockLevelRepository;
import com.lumora.pos.loyalty.dto.LoyaltyConfig;
import com.lumora.pos.loyalty.service.LoyaltyService;
import com.lumora.pos.sales.dto.SaleRequest;
import com.lumora.pos.sales.dto.SaleResponse;
import com.lumora.pos.sales.entity.SaleEntity;
import com.lumora.pos.sales.repository.SaleRepository;
import com.lumora.pos.tax.service.TaxRateService;
import com.lumora.pos.tenant.service.TenantInfoService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SaleService — the most critical service in the POS system.
 * 
 * These tests focus on:
 * 1. Financial math accuracy (totals, tax, discounts, net amounts)
 * 2. Stock deduction correctness
 * 3. Edge cases (insufficient stock, missing products, zero-discount)
 * 4. Audit logging invocation
 * 
 * All dependencies are mocked — no database required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SaleService Unit Tests")
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BranchAccessGuard branchAccessGuard;

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private TaxRateService taxRateService;

    @Mock
    private CashSessionService cashSessionService;

    @Mock
    private CashSessionRepository cashSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoyaltyService loyaltyService;

    @Mock
    private TenantInfoService tenantInfoService;

    @InjectMocks
    private SaleService saleService;

    // Reusable test data
    private UUID productId1;
    private UUID productId2;
    private UUID branchId;
    private ProductEntity product1;
    private ProductEntity product2;
    private BranchEntity mockBranch;
    private StockLevelEntity mockStockLevel1;
    private StockLevelEntity mockStockLevel2;

    @BeforeEach
    void setUp() {
        TestUtils.setupDefaultContext();

        productId1 = UUID.randomUUID();
        productId2 = UUID.randomUUID();
        branchId = UUID.randomUUID();

        product1 = ProductEntity.builder()
                .name("Widget A")
                .sku("WDG-001")
                .basePrice(new BigDecimal("100.00"))
                .costPrice(new BigDecimal("60.00"))
                .stockQuantity(50)
                .lowStockThreshold(5)
                .isActive(true)
                .build();
        product1.setId(productId1);
        product1.setTenantId(TestUtils.TEST_TENANT_ID);

        product2 = ProductEntity.builder()
                .name("Widget B")
                .sku("WDG-002")
                .basePrice(new BigDecimal("200.00"))
                .costPrice(new BigDecimal("120.00"))
                .stockQuantity(30)
                .lowStockThreshold(3)
                .isActive(true)
                .build();
        product2.setId(productId2);
        product2.setTenantId(TestUtils.TEST_TENANT_ID);

        mockBranch = new BranchEntity();
        mockBranch.setId(branchId);

        mockStockLevel1 = StockLevelEntity.builder().quantity(50).build();
        mockStockLevel1.setId(UUID.randomUUID());

        mockStockLevel2 = StockLevelEntity.builder().quantity(30).build();
        mockStockLevel2.setId(UUID.randomUUID());

        // Lenient: common stubs not consumed by every test (e.g. dashboard summary, missing-product tests)
        lenient().when(branchRepository.findByIsDefaultTrueAndTenantId(TestUtils.TEST_TENANT_ID))
                .thenReturn(Optional.of(mockBranch));
        lenient().when(stockLevelRepository.findByProductAndBranchForUpdate(productId1, branchId, TestUtils.TEST_TENANT_ID))
                .thenReturn(Optional.of(mockStockLevel1));
        lenient().when(stockLevelRepository.findByProductAndBranchForUpdate(productId2, branchId, TestUtils.TEST_TENANT_ID))
                .thenReturn(Optional.of(mockStockLevel2));
        lenient().when(taxRateService.getApplicableRate(any(ProductEntity.class)))
                .thenReturn(new BigDecimal("0.10"));
        lenient().when(stockLevelRepository.save(any(StockLevelEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cashSessionService.findActiveEntityByUserId(any(UUID.class)))
                .thenReturn(Optional.empty());
        // createSale always reads loyalty config; default program (earn 1pt/10, redeem 0.10).
        lenient().when(loyaltyService.getConfig()).thenReturn(LoyaltyConfig.defaults());
    }

    @AfterEach
    void tearDown() {
        TestUtils.clearContext();
    }

    // ======================================================================
    // Helper Methods
    // ======================================================================

    private SaleRequest.SaleItemRequest createItemRequest(UUID productId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discount) {
        SaleRequest.SaleItemRequest item = new SaleRequest.SaleItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setDiscountAmount(discount);
        return item;
    }

    private SaleRequest createSaleRequest(String paymentMethod,
            SaleRequest.SaleItemRequest... items) {
        return SaleRequest.builder()
                .paymentMethod(paymentMethod)
                .items(Arrays.asList(items))
                .build();
    }

    private void stubProductLookup(ProductEntity product) {
        when(productRepository.findByIdAndTenantId(product.getId(), TestUtils.TEST_TENANT_ID))
                .thenReturn(Optional.of(product));
    }

    private void stubSaleRepositorySave() {
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(invocation -> {
            SaleEntity sale = invocation.getArgument(0);
            if (sale.getId() == null) {
                sale.setId(UUID.randomUUID());
            }
            // Simulate items getting IDs assigned
            sale.getItems().forEach(item -> {
                if (item.getId() == null) {
                    item.setId(UUID.randomUUID());
                }
            });
            return sale;
        });
    }

    private void stubStockLevel(UUID productId, int quantity) {
        StockLevelEntity sl = StockLevelEntity.builder().quantity(quantity).build();
        sl.setId(UUID.randomUUID());
        when(stockLevelRepository.findByProductAndBranchForUpdate(productId, branchId, TestUtils.TEST_TENANT_ID))
                .thenReturn(Optional.of(sl));
    }

    // ======================================================================
    // 1. Financial Math — Single Item
    // ======================================================================

    @Nested
    @DisplayName("Single Item Sales")
    class SingleItemSales {

        @Test
        @DisplayName("Should calculate correct totals for a single item without discount")
        void singleItem_noDiscount() {
            // Given: 1 Widget A at $100, quantity 2, no discount
            stubProductLookup(product1);
            stubSaleRepositorySave();

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("2"), new BigDecimal("100.00"), null));

            // When
            SaleResponse response = saleService.createSale(request);

            // Then
            // subtotal = 100 * 2 = 200
            // discount = 0
            // tax = (200 - 0) * 0.10 = 20
            // item total = 200 - 0 + 20 = 220
            // net = 200 - 0 + 20 = 220
            assertThat(response.getTotalAmount()).isEqualByComparingTo("200.00");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo("0");
            assertThat(response.getTaxAmount()).isEqualByComparingTo("20.00");
            assertThat(response.getNetAmount()).isEqualByComparingTo("220.00");
        }

        @Test
        @DisplayName("Should calculate correct totals for a single item with discount")
        void singleItem_withDiscount() {
            // Given: 1 Widget A at $100, quantity 3, discount $15
            stubProductLookup(product1);
            stubSaleRepositorySave();

            SaleRequest request = createSaleRequest("CARD",
                    createItemRequest(productId1, new BigDecimal("3"), new BigDecimal("100.00"),
                            new BigDecimal("15.00")));

            // When
            SaleResponse response = saleService.createSale(request);

            // Then
            // subtotal = 100 * 3 = 300
            // discount = 15
            // tax = (300 - 15) * 0.10 = 28.50
            // item total = 300 - 15 + 28.50 = 313.50
            // net = 300 - 15 + 28.50 = 313.50
            assertThat(response.getTotalAmount()).isEqualByComparingTo("300.00");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo("15.00");
            assertThat(response.getTaxAmount()).isEqualByComparingTo("28.50");
            assertThat(response.getNetAmount()).isEqualByComparingTo("313.50");
        }

        @Test
        @DisplayName("Should reject fractional quantities (stock is integer-modelled)")
        void singleItem_fractionalQuantity_rejected() {
            // Given: Widget A at $10, quantity 1.5. Stock is tracked as whole
            // units, so SaleService rejects fractional quantities rather than
            // truncating and silently deducting the wrong amount.
            stubProductLookup(product1);

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("1.5"), new BigDecimal("10.00"), null));

            // When / Then
            assertThatThrownBy(() -> saleService.createSale(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Fractional quantities are not supported");
        }
    }

    // ======================================================================
    // 2. Financial Math — Multi-Item
    // ======================================================================

    @Nested
    @DisplayName("Multi-Item Sales")
    class MultiItemSales {

        @Test
        @DisplayName("Should sum totals correctly across multiple items")
        void multipleItems_correctTotals() {
            // Given: 2x Widget A at $100, 1x Widget B at $200
            stubProductLookup(product1);
            stubProductLookup(product2);
            stubSaleRepositorySave();

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("2"), new BigDecimal("100.00"), null),
                    createItemRequest(productId2, new BigDecimal("1"), new BigDecimal("200.00"),
                            new BigDecimal("10.00")));

            // When
            SaleResponse response = saleService.createSale(request);

            // Then
            // Item 1: subtotal=200, discount=0, tax=(200)*0.10=20, total=220
            // Item 2: subtotal=200, discount=10, tax=(200-10)*0.10=19, total=209
            // Sale totals: totalAmount=400, discount=10, tax=39, net=400-10+39=429
            assertThat(response.getTotalAmount()).isEqualByComparingTo("400.00");
            assertThat(response.getDiscountAmount()).isEqualByComparingTo("10.00");
            assertThat(response.getTaxAmount()).isEqualByComparingTo("39.00");
            assertThat(response.getNetAmount()).isEqualByComparingTo("429.00");
            assertThat(response.getItems()).hasSize(2);
        }
    }

    // ======================================================================
    // 3. Stock Management
    // ======================================================================

    @Nested
    @DisplayName("Stock Management")
    class StockManagement {

        @Test
        @DisplayName("Should deduct stock after successful sale")
        void shouldDeductStock() {
            // Given: StockLevel has 50 units, selling 3
            stubProductLookup(product1);
            stubSaleRepositorySave();

            ArgumentCaptor<StockLevelEntity> stockCaptor = ArgumentCaptor.forClass(StockLevelEntity.class);
            when(stockLevelRepository.save(stockCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("3"), new BigDecimal("100.00"), null));

            // When
            saleService.createSale(request);

            // Then: Stock level should be deducted from 50 → 47
            StockLevelEntity savedStock = stockCaptor.getValue();
            assertThat(savedStock.getQuantity()).isEqualTo(47);
        }

        @Test
        @DisplayName("Should throw BusinessException when stock is insufficient")
        void shouldThrowOnInsufficientStock() {
            // Given: Product has 50 units, trying to sell 100
            stubProductLookup(product1);

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("100"), new BigDecimal("100.00"), null));

            // When & Then
            assertThatThrownBy(() -> saleService.createSale(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Insufficient stock");
        }

        @Test
        @DisplayName("Should throw when product is not found")
        void shouldThrowOnMissingProduct() {
            // Given: Product doesn't exist
            UUID unknownId = UUID.randomUUID();
            when(productRepository.findByIdAndTenantId(unknownId, TestUtils.TEST_TENANT_ID))
                    .thenReturn(Optional.empty());

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(unknownId, new BigDecimal("1"), new BigDecimal("50.00"), null));

            // When & Then
            assertThatThrownBy(() -> saleService.createSale(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Product not found");
        }
    }

    // ======================================================================
    // 4. Payment Information
    // ======================================================================

    @Nested
    @DisplayName("Payment Information")
    class PaymentInfo {

        @Test
        @DisplayName("Should set payment method correctly")
        void shouldSetPaymentMethod() {
            stubProductLookup(product1);
            stubSaleRepositorySave();

            SaleRequest request = createSaleRequest("CARD",
                    createItemRequest(productId1, new BigDecimal("1"), new BigDecimal("100.00"), null));

            SaleResponse response = saleService.createSale(request);

            assertThat(response.getPaymentMethod()).isEqualTo("CARD");
            assertThat(response.getPaymentStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("Should generate an invoice number")
        void shouldGenerateInvoiceNumber() {
            stubProductLookup(product1);
            stubSaleRepositorySave();

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("1"), new BigDecimal("100.00"), null));

            SaleResponse response = saleService.createSale(request);

            assertThat(response.getInvoiceNumber()).startsWith("INV-");
        }
    }

    // ======================================================================
    // 5. Audit Logging
    // ======================================================================

    @Nested
    @DisplayName("Audit Logging")
    class AuditLogging {

        @Test
        @DisplayName("Should call auditService.log with SALE_CREATE after successful sale")
        void shouldLogAuditOnSuccess() {
            stubProductLookup(product1);
            stubSaleRepositorySave();

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("1"), new BigDecimal("100.00"), null));

            saleService.createSale(request);

            // Verify audit was called exactly once with SALE_CREATE
            verify(auditService, times(1)).log(
                    eq(AuditAction.SALE_CREATE),
                    eq("SALE"),
                    any(UUID.class),
                    isNull(),
                    any(SaleResponse.class));
        }

        @Test
        @DisplayName("Should NOT log audit when sale fails due to insufficient stock")
        void shouldNotLogAuditOnFailure() {
            stubProductLookup(product1);

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("100"), new BigDecimal("100.00"), null));

            assertThatThrownBy(() -> saleService.createSale(request))
                    .isInstanceOf(BusinessException.class);

            // Verify audit was NEVER called
            verify(auditService, never()).log(any(), any(), any(), any(), any());
        }
    }

    // ======================================================================
    // 6. Edge Cases
    // ======================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle zero discount correctly")
        void zeroDiscount() {
            stubProductLookup(product1);
            stubSaleRepositorySave();

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("1"), new BigDecimal("100.00"), BigDecimal.ZERO));

            SaleResponse response = saleService.createSale(request);

            assertThat(response.getDiscountAmount()).isEqualByComparingTo("0");
            assertThat(response.getTotalAmount()).isEqualByComparingTo("100.00");
            assertThat(response.getTaxAmount()).isEqualByComparingTo("10.00");
            assertThat(response.getNetAmount()).isEqualByComparingTo("110.00");
        }

        @Test
        @DisplayName("Should handle quantity of exactly 1")
        void singleQuantity() {
            stubProductLookup(product1);
            stubSaleRepositorySave();

            // Unit price is authoritative from the catalog: the request value is
            // ignored for catalog lines, so totals follow Widget A's basePrice (100).
            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("1"), new BigDecimal("50.00"), null));

            SaleResponse response = saleService.createSale(request);

            // subtotal=100, tax=10, net=110
            assertThat(response.getTotalAmount()).isEqualByComparingTo("100.00");
            assertThat(response.getTaxAmount()).isEqualByComparingTo("10.00");
            assertThat(response.getNetAmount()).isEqualByComparingTo("110.00");
        }

        @Test
        @DisplayName("Inclusive pricing extracts VAT from the price instead of adding it")
        void inclusivePricing_extractsVatFromPrice() {
            stubProductLookup(product1);
            stubSaleRepositorySave();
            when(taxRateService.getApplicableRate(any(ProductEntity.class)))
                    .thenReturn(new BigDecimal("0.18"));
            when(tenantInfoService.isTaxInclusive(any())).thenReturn(true);

            // 1 × Widget A @ 100 VAT-inclusive (18%): net = 100/1.18 = 84.75,
            // VAT = 15.25, and the customer still pays exactly 100.
            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("1"), new BigDecimal("100.00"), null));

            SaleResponse response = saleService.createSale(request);

            assertThat(response.isTaxInclusive()).isTrue();
            assertThat(response.getTotalAmount()).isEqualByComparingTo("100.00");
            assertThat(response.getTaxAmount()).isEqualByComparingTo("15.25");
            assertThat(response.getNetAmount()).isEqualByComparingTo("100.00");
            assertThat(response.getItems().get(0).getTaxAmount()).isEqualByComparingTo("15.25");
            assertThat(response.getItems().get(0).getTotalAmount()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Should sell exactly all remaining stock without error")
        void exactStockMatch() {
            stubStockLevel(productId1, 5);
            stubProductLookup(product1);
            stubSaleRepositorySave();

            SaleRequest request = createSaleRequest("CASH",
                    createItemRequest(productId1, new BigDecimal("5"), new BigDecimal("100.00"), null));

            // Should NOT throw — selling exactly what's available
            SaleResponse response = saleService.createSale(request);
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("Dashboard Summary")
    class DashboardSummary {

        @Test
        @DisplayName("Should aggregate daily summary correctly from repository results")
        void shouldCalculateDailySummary() {
            // Given
            Object[] row1 = new Object[] {
                SaleEntity.PaymentMethod.CASH,
                5L, // count
                new BigDecimal("500.00"), // gross
                new BigDecimal("50.00"),  // tax
                new BigDecimal("10.00"),  // discount
                new BigDecimal("540.00")  // net
            };
            Object[] row2 = new Object[] {
                SaleEntity.PaymentMethod.CARD,
                3L, // count
                new BigDecimal("300.00"), // gross
                new BigDecimal("30.00"),  // tax
                new BigDecimal("5.00"),   // discount
                new BigDecimal("325.00")  // net
            };

            when(saleRepository.aggregateDailySummary(eq(TestUtils.TEST_TENANT_ID), any(), any()))
                .thenReturn(Arrays.asList(row1, row2));

            // When
            com.lumora.pos.sales.dto.SalesSummaryResponse summary = saleService.getDailySummary();

            // Then
            assertThat(summary.getTotalOrders()).isEqualTo(8);
            assertThat(summary.getTotalGrossSales()).isEqualByComparingTo("800.00");
            assertThat(summary.getTotalTax()).isEqualByComparingTo("80.00");
            assertThat(summary.getTotalDiscounts()).isEqualByComparingTo("15.00");
            assertThat(summary.getTotalNetSales()).isEqualByComparingTo("865.00");
            assertThat(summary.getSalesByPaymentMethod()).containsEntry("CASH", new BigDecimal("540.00"));
            assertThat(summary.getSalesByPaymentMethod()).containsEntry("CARD", new BigDecimal("325.00"));
        }
    }
}
