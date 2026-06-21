package com.lumora.pos.inventory.service;

import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.branch.service.BranchAccessGuard;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.tenant.TenantContext;
import com.lumora.pos.inventory.dto.InventoryAdjustmentRequest;
import com.lumora.pos.inventory.dto.InventoryAdjustmentResponse;
import com.lumora.pos.inventory.dto.StockTransferRequest;
import com.lumora.pos.inventory.entity.InventoryAdjustmentEntity;
import com.lumora.pos.inventory.entity.ProductEntity;
import com.lumora.pos.inventory.entity.StockLevelEntity;
import com.lumora.pos.inventory.repository.InventoryAdjustmentRepository;
import com.lumora.pos.inventory.repository.ProductRepository;
import com.lumora.pos.inventory.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final StockLevelRepository stockLevelRepository;
    private final BranchAccessGuard branchAccessGuard;

    @Transactional
    public void adjustStock(InventoryAdjustmentRequest request) {
        // Direct adjustment: the caller must have access to the target branch.
        branchAccessGuard.assertCanAccess(request.getBranchId());
        applyAdjustment(request);
    }

    private void applyAdjustment(InventoryAdjustmentRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        ProductEntity product = productRepository.findByIdAndTenantId(request.getProductId(), tenantId)
                .orElseThrow(() -> new BusinessException("Product not found"));

        BranchEntity branch = branchRepository.findByIdAndTenantId(request.getBranchId(), tenantId)
                .orElseThrow(() -> new BusinessException("Branch not found"));

        StockLevelEntity stockLevel = stockLevelRepository
                .findByProductIdAndBranchIdAndTenantId(product.getId(), branch.getId(), tenantId)
                .orElseGet(() -> createInitialStockLevel(product, branch, tenantId));

        int previousQuantity = stockLevel.getQuantity();
        int newQuantity;
        int delta;

        switch (request.getType()) {
            case RECONCILIATION:
                newQuantity = request.getQuantity();
                delta = newQuantity - previousQuantity;
                break;
            case STOCK_IN:
            case RETURN:
            case TRANSFER_IN:
                delta = request.getQuantity();
                newQuantity = previousQuantity + delta;
                break;
            case STOCK_OUT:
            case DAMAGE:
            case TRANSFER_OUT:
            case SALE:
                delta = -request.getQuantity();
                newQuantity = previousQuantity + delta;
                break;
            default:
                throw new BusinessException("Unsupported adjustment type");
        }

        if (newQuantity < 0) {
            throw new BusinessException("Insufficient stock in branch: " + branch.getName());
        }

        // 1. Update Stock Level
        stockLevel.setQuantity(newQuantity);
        stockLevelRepository.save(stockLevel);

        // 2. Step skipped as Product stock is now derived from StockLevelEntity totals

        // 3. Log Adjustment
        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity();
        adjustment.setTenantId(tenantId);
        adjustment.setProduct(product);
        adjustment.setBranch(branch);
        adjustment.setType(request.getType());
        adjustment.setQuantity(Math.abs(delta));
        adjustment.setPreviousQuantity(previousQuantity);
        adjustment.setNewQuantity(newQuantity);
        adjustment.setReason(request.getReason());
        adjustment.setReferenceId(request.getReferenceId());
        adjustmentRepository.save(adjustment);
    }

    @Transactional
    public void transferStock(StockTransferRequest request) {
        // A transfer requires access to the SOURCE branch only; stock may be sent to any
        // tenant branch. assertCanAccess on the two legs would wrongly require destination access.
        branchAccessGuard.assertCanAccess(request.getSourceBranchId());

        String transferRef = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Out from source
        InventoryAdjustmentRequest outRequest = new InventoryAdjustmentRequest();
        outRequest.setProductId(request.getProductId());
        outRequest.setBranchId(request.getSourceBranchId());
        outRequest.setType(InventoryAdjustmentEntity.AdjustmentType.TRANSFER_OUT);
        outRequest.setQuantity(request.getQuantity());
        outRequest.setReason("Transfer to branch. " + (request.getNotes() != null ? request.getNotes() : ""));
        outRequest.setReferenceId(transferRef);
        applyAdjustment(outRequest);

        // In to destination
        InventoryAdjustmentRequest inRequest = new InventoryAdjustmentRequest();
        inRequest.setProductId(request.getProductId());
        inRequest.setBranchId(request.getDestinationBranchId());
        inRequest.setType(InventoryAdjustmentEntity.AdjustmentType.TRANSFER_IN);
        inRequest.setQuantity(request.getQuantity());
        inRequest.setReason("Transfer from branch. " + (request.getNotes() != null ? request.getNotes() : ""));
        inRequest.setReferenceId(transferRef);
        applyAdjustment(inRequest);
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustmentsByProduct(UUID productId) {
        UUID tenantId = TenantContext.getTenantId();
        return adjustmentRepository.findByProductIdAndTenantIdOrderByCreatedAtDesc(productId, tenantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private InventoryAdjustmentResponse mapToResponse(InventoryAdjustmentEntity entity) {
        return InventoryAdjustmentResponse.builder()
                .id(entity.getId())
                .productName(entity.getProduct().getName())
                .branchName(entity.getBranch().getName())
                .type(entity.getType())
                .quantity(entity.getQuantity())
                .previousQuantity(entity.getPreviousQuantity())
                .newQuantity(entity.getNewQuantity())
                .reason(entity.getReason())
                .referenceId(entity.getReferenceId())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private StockLevelEntity createInitialStockLevel(ProductEntity product, BranchEntity branch, UUID tenantId) {
        StockLevelEntity sl = StockLevelEntity.builder()
                .product(product)
                .branch(branch)
                .quantity(0)
                .build();
        sl.setTenantId(tenantId);
        return stockLevelRepository.save(sl);
    }
}
