package com.lumora.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockResponse {
    private UUID productId;
    private String productName;
    private String productSku;
    private UUID branchId;
    private String branchName;
    private Integer currentQuantity;
    private Integer threshold;
}
