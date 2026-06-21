package com.lumora.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {

    private UUID id;

    // Source branch
    private UUID sourceBranchId;
    private String sourceBranchName;

    // Destination branch
    private UUID destinationBranchId;
    private String destinationBranchName;

    // Product
    private UUID productId;
    private String productName;
    private String productSku;

    private Integer quantity;
    private String status;
    private String notes;

    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private String createdByName;
}
