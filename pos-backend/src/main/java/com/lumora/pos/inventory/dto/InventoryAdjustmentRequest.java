package com.lumora.pos.inventory.dto;

import com.lumora.pos.inventory.entity.InventoryAdjustmentEntity;
import lombok.Data;

import java.util.UUID;

@Data
public class InventoryAdjustmentRequest {
    private UUID productId;
    private UUID branchId;
    private InventoryAdjustmentEntity.AdjustmentType type;
    private Integer quantity;
    private String reason;
    private String referenceId;
}
