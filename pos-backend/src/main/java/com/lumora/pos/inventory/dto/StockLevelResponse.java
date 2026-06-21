package com.lumora.pos.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StockLevelResponse {
    private UUID id;
    private UUID productId;
    private UUID branchId;
    private String branchName;
    private Integer quantity;
}
