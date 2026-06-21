package com.lumora.pos.sales.service;

import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.inventory.entity.ProductEntity;
import com.lumora.pos.inventory.entity.StockLevelEntity;
import com.lumora.pos.inventory.repository.ProductRepository;
import com.lumora.pos.inventory.repository.StockLevelRepository;
import com.lumora.pos.sales.dto.SaleRequest;
import com.lumora.pos.sales.dto.SaleResponse;
import com.lumora.pos.sales.repository.SaleRepository;
import com.lumora.pos.tenant.TenantContext;
import com.lumora.pos.tax.entity.TaxRateEntity;
import com.lumora.pos.tax.repository.TaxRateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SaleServiceIntegrationTest {

    @Autowired
    private SaleService saleService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private StockLevelRepository stockLevelRepository;

    @Autowired
    private TaxRateRepository taxRateRepository;

    @Autowired
    private SaleRepository saleRepository;

    private UUID tenantId;
    private BranchEntity branch;
    private ProductEntity product;
    private StockLevelEntity stockLevel;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        // 1. Create Branch
        branch = BranchEntity.builder()
                .name("Test Branch")
                .isDefault(true)
                .isActive(true)
                .build();
        branch.setTenantId(tenantId);
        branch = branchRepository.save(branch);

        // 2. Create Tax Rate (default 10%)
        TaxRateEntity tax = TaxRateEntity.builder()
                .name("Standard")
                .rate(new BigDecimal("0.10"))
                .isDefault(true)
                .build();
        tax.setTenantId(tenantId);
        taxRateRepository.save(tax);

        // 3. Create Product
        product = ProductEntity.builder()
                .name("Integration Widget")
                .sku("INT-001")
                .basePrice(new BigDecimal("100.00"))
                .lowStockThreshold(5)
                .isActive(true)
                .build();
        product.setTenantId(tenantId);
        product = productRepository.save(product);

        // 4. Create Initial Stock (50 units)
        stockLevel = StockLevelEntity.builder()
                .product(product)
                .branch(branch)
                .quantity(50)
                .build();
        stockLevel.setTenantId(tenantId);
        stockLevel = stockLevelRepository.save(stockLevel);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createSale_shouldDeductStockAndCalculateTotals() {
        // Given
        SaleRequest.SaleItemRequest itemReq = new SaleRequest.SaleItemRequest();
        itemReq.setProductId(product.getId());
        itemReq.setQuantity(new BigDecimal("2"));
        itemReq.setUnitPrice(new BigDecimal("100.00"));

        SaleRequest request = SaleRequest.builder()
                .branchId(branch.getId())
                .paymentMethod("CASH")
                .items(Collections.singletonList(itemReq))
                .build();

        // When
        SaleResponse response = saleService.createSale(request);

        // Then
        // Default pricing mode is VAT-inclusive: 2 × 100 = 200 paid, with 10% VAT
        // extracted (net 181.82 + VAT 18.18). The customer pays 200, not 220.
        assertThat(response.isTaxInclusive()).isTrue();
        assertThat(response.getNetAmount()).isEqualByComparingTo("200.00");
        assertThat(response.getTaxAmount()).isEqualByComparingTo("18.18");
        assertThat(response.getInvoiceNumber()).startsWith("INV-");

        // Verify Stock Deduction via Repository (formula field on product needs refresh to be accurate in test)
        StockLevelEntity updatedStock = stockLevelRepository.findById(stockLevel.getId()).orElseThrow();
        assertThat(updatedStock.getQuantity()).isEqualTo(48); // 50 - 2
    }

    @Test
    void createSale_withCustomLine_billsWithoutStockOrProduct() {
        // Prices are VAT-inclusive (default). Catalog line: product x2 @ 100 = 200 paid.
        SaleRequest.SaleItemRequest catalog = new SaleRequest.SaleItemRequest();
        catalog.setProductId(product.getId());
        catalog.setQuantity(new BigDecimal("2"));
        catalog.setUnitPrice(new BigDecimal("100.00"));

        // Custom/open line: not in catalog, x2 @ 50 = 100 paid (default 10% VAT extracted)
        SaleRequest.SaleItemRequest custom = new SaleRequest.SaleItemRequest();
        custom.setItemName("Loose screws");
        custom.setQuantity(new BigDecimal("2"));
        custom.setUnitPrice(new BigDecimal("50.00"));

        SaleRequest request = SaleRequest.builder()
                .branchId(branch.getId())
                .paymentMethod("CASH")
                .items(java.util.List.of(catalog, custom))
                .build();

        SaleResponse response = saleService.createSale(request);

        // Inclusive: customer pays 200 (catalog) + 100 (custom) = 300.
        assertThat(response.getNetAmount()).isEqualByComparingTo("300.00");

        // Custom line: null productId, typed name surfaced, VAT extracted from the
        // inclusive 100 (100 − 100/1.10 = 9.09); the line total paid is 100.
        SaleResponse.SaleItemResponse customLine = response.getItems().stream()
                .filter(i -> i.getProductId() == null).findFirst().orElseThrow();
        assertThat(customLine.getProductName()).isEqualTo("Loose screws");
        assertThat(customLine.getTaxAmount()).isEqualByComparingTo("9.09");
        assertThat(customLine.getTotalAmount()).isEqualByComparingTo("100.00");

        // Stock deducted only for the catalog line (custom line touches no stock).
        StockLevelEntity updatedStock = stockLevelRepository.findById(stockLevel.getId()).orElseThrow();
        assertThat(updatedStock.getQuantity()).isEqualTo(48); // 50 - 2, not -4

        // Product-grouped aggregates must exclude the custom line's null productId
        // (otherwise the dashboard/profitability reports NPE on productId.toString()).
        java.time.LocalDateTime from = java.time.LocalDateTime.now().minusDays(1);
        java.time.LocalDateTime to = java.time.LocalDateTime.now().plusDays(1);
        var topProducts = saleRepository.findTopSellingProducts(
                tenantId, from, to, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(topProducts).hasSize(1);
        assertThat(topProducts).allSatisfy(r -> assertThat(r[0]).isNotNull());
        assertThat(saleRepository.aggregateProductProfitability(tenantId, from, to))
                .allSatisfy(r -> assertThat(r[0]).isNotNull());
    }
}
