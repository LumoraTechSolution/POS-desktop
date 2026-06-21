package com.lumora.pos.inventory.dto;

import com.lumora.pos.inventory.entity.InventoryAdjustmentEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class InventoryAdjustmentResponse {
    private UUID id;
    private String productName;
    private String branchName;
    private InventoryAdjustmentEntity.AdjustmentType type;
    private Integer quantity;
    private Integer previousQuantity;
    private Integer newQuantity;
    private String reason;
    private String referenceId;
    private LocalDateTime createdAt;
}
