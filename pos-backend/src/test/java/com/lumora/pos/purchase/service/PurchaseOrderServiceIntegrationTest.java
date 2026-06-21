package com.lumora.pos.purchase.service;

import com.lumora.pos.auth.entity.UserEntity;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.inventory.entity.ProductEntity;
import com.lumora.pos.inventory.entity.StockLevelEntity;
import com.lumora.pos.inventory.repository.ProductRepository;
import com.lumora.pos.inventory.repository.StockLevelRepository;
import com.lumora.pos.purchase.dto.PurchaseOrderRequest;
import com.lumora.pos.purchase.dto.PurchaseOrderResponse;
import com.lumora.pos.purchase.dto.ReceivePoItemRequest;
import com.lumora.pos.purchase.entity.PurchaseOrderEntity;
import com.lumora.pos.purchase.repository.PurchaseOrderRepository;
import com.lumora.pos.supplier.entity.SupplierEntity;
import com.lumora.pos.supplier.repository.SupplierRepository;
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
 * Verifies the receive-PO path actually moves stock. The variance side
 * (under-receive vs. over-order) is the easy place to introduce silent bugs:
 * a service that forgets to call the stock-update would leave PO status
 * RECEIVED but inventory unchanged. These tests fail loudly if that happens.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchaseOrderServiceIntegrationTest {

    @Autowired private PurchaseOrderService purchaseOrderService;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockLevelRepository stockLevelRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private BranchEntity branch;
    private SupplierEntity supplier;
    private ProductEntity product;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        UserEntity admin = UserEntity.builder()
                .email("admin-" + UUID.randomUUID() + "@test.local")
                .passwordHash("x")
                .firstName("Test").lastName("Admin")
                .isActive(true)
                .build();
        admin.setTenantId(tenantId);
        admin = userRepository.save(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin.getId(), null, Collections.emptyList()));

        branch = BranchEntity.builder().name("Warehouse").isDefault(true).isActive(true).build();
        branch.setTenantId(tenantId);
        branch = branchRepository.save(branch);

        supplier = SupplierEntity.builder().name("Acme Supply").isActive(true).build();
        supplier.setTenantId(tenantId);
        supplier = supplierRepository.save(supplier);

        product = ProductEntity.builder()
                .name("Widget")
                .sku("W-1")
                .basePrice(new BigDecimal("10.00"))
                .costPrice(new BigDecimal("5.00"))
                .stockQuantity(20)
                .lowStockThreshold(1)
                .isActive(true)
                .build();
        product.setTenantId(tenantId);
        product = productRepository.save(product);

        // Seed initial stock at the receiving branch.
        StockLevelEntity stock = StockLevelEntity.builder()
                .product(product).branch(branch).quantity(20).build();
        stock.setTenantId(tenantId);
        stockLevelRepository.save(stock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void receivePO_fullyReceived_increasesStockAndMarksReceived() {
        PurchaseOrderResponse po = createDraftPo(50, new BigDecimal("4.00"));
        UUID poItemId = po.getItems().get(0).getId();

        purchaseOrderService.receivePurchaseOrder(po.getId(), List.of(
                ReceivePoItemRequest.builder().poItemId(poItemId).receivedQuantity(50).build()));

        StockLevelEntity stock = stockLevelRepository
                .findByProductIdAndBranchIdAndTenantId(product.getId(), branch.getId(), tenantId)
                .orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(70); // 20 seed + 50 received

        PurchaseOrderEntity persisted = purchaseOrderRepository.findById(po.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PurchaseOrderEntity.POStatus.RECEIVED);

        // Cost price gets pulled from the PO's unit cost.
        ProductEntity refreshed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(refreshed.getCostPrice()).isEqualByComparingTo("4.00");
    }

    @Test
    void receivePO_partialReceive_leavesStatusAsPartial() {
        PurchaseOrderResponse po = createDraftPo(10, new BigDecimal("3.00"));
        UUID poItemId = po.getItems().get(0).getId();

        purchaseOrderService.receivePurchaseOrder(po.getId(), List.of(
                ReceivePoItemRequest.builder().poItemId(poItemId).receivedQuantity(4).build()));

        StockLevelEntity stock = stockLevelRepository
                .findByProductIdAndBranchIdAndTenantId(product.getId(), branch.getId(), tenantId)
                .orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(24); // 20 + 4

        PurchaseOrderEntity persisted = purchaseOrderRepository.findById(po.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PurchaseOrderEntity.POStatus.PARTIAL);
    }

    @Test
    void receivePO_overReceive_throwsAndLeavesStockUntouched() {
        PurchaseOrderResponse po = createDraftPo(10, new BigDecimal("3.00"));
        UUID poItemId = po.getItems().get(0).getId();

        assertThatThrownBy(() -> purchaseOrderService.receivePurchaseOrder(po.getId(), List.of(
                ReceivePoItemRequest.builder().poItemId(poItemId).receivedQuantity(11).build())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot receive more than ordered");

        StockLevelEntity stock = stockLevelRepository
                .findByProductIdAndBranchIdAndTenantId(product.getId(), branch.getId(), tenantId)
                .orElseThrow();
        assertThat(stock.getQuantity()).isEqualTo(20); // unchanged
    }

    @Test
    void receivePO_alreadyReceived_throws() {
        PurchaseOrderResponse po = createDraftPo(2, new BigDecimal("1.00"));
        UUID poItemId = po.getItems().get(0).getId();

        purchaseOrderService.receivePurchaseOrder(po.getId(), List.of(
                ReceivePoItemRequest.builder().poItemId(poItemId).receivedQuantity(2).build()));

        assertThatThrownBy(() -> purchaseOrderService.receivePurchaseOrder(po.getId(), List.of(
                ReceivePoItemRequest.builder().poItemId(poItemId).receivedQuantity(1).build())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already fully received");
    }

    private PurchaseOrderResponse createDraftPo(int qty, BigDecimal unitCost) {
        PurchaseOrderRequest.PurchaseOrderItemRequest itemReq = PurchaseOrderRequest.PurchaseOrderItemRequest.builder()
                .productId(product.getId())
                .quantity(qty)
                .unitCost(unitCost)
                .build();

        PurchaseOrderRequest req = PurchaseOrderRequest.builder()
                .supplierId(supplier.getId())
                .branchId(branch.getId())
                .items(List.of(itemReq))
                .build();

        return purchaseOrderService.createPurchaseOrder(req);
    }
}
