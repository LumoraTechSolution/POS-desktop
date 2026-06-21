package com.lumora.pos.returns.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ReturnItemResponse {
    private UUID id;
    private UUID saleItemId;
    private UUID productId;
    private String productName;
    private BigDecimal quantityReturned;
    private BigDecimal unitPrice;
    private BigDecimal refundAmount;
}
