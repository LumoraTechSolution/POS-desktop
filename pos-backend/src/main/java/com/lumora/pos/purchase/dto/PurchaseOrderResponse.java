package com.lumora.pos.purchase.dto;

import com.lumora.pos.purchase.entity.PurchaseOrderEntity.POStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PurchaseOrderResponse {
    private UUID id;
    private String poNumber;
    private UUID supplierId;
    private String supplierName;
    private UUID branchId;
    private String branchName;
    private POStatus status;
    private LocalDateTime expectedDate;
    private BigDecimal totalAmount;
    private String notes;
    private UUID createdBy;
    private String createdByName;
    private UUID receivedBy;
    private String receivedByName;
    private List<PurchaseOrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class PurchaseOrderItemResponse {
        private UUID id;
        private UUID productId;
        private String productName;
        private String sku;
        private Integer orderedQuantity;
        private Integer receivedQuantity;
        private BigDecimal unitCost;
        private BigDecimal totalCost;
    }
}
