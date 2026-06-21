package com.lumora.pos.system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InventoryHealthResponse {
    private boolean healthy;
    private int totalProductsChecked;
    private int discrepancyCount;
    private List<ProductDiscrepancy> discrepancies;

    @Data
    @Builder
    public static class ProductDiscrepancy {
        private UUID productId;
        private String productName;
        private Integer globalStock;
        private Long calculatedBranchStock;
        private Long difference;
    }
}
