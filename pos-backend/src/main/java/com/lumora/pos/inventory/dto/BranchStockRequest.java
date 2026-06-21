package com.lumora.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchStockRequest {
    private UUID branchId;
    private Integer quantity;
}
