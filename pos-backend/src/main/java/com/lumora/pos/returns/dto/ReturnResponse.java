package com.lumora.pos.returns.dto;

import com.lumora.pos.returns.entity.ReturnEntity.RefundMethod;
import com.lumora.pos.returns.entity.ReturnEntity.ReturnStatus;
import com.lumora.pos.returns.entity.ReturnEntity.ReturnType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReturnResponse {
    private UUID id;
    private UUID saleId;
    private String returnNumber;
    private String invoiceNumber;
    private String reason;
    private ReturnType returnType;
    private ReturnStatus status;
    private BigDecimal refundAmount;
    private RefundMethod refundMethod;
    private UUID processedBy;
    private String processedByName;
    private UUID approvedBy;
    private String approvedByName;
    private String notes;
    private UUID exchangeSaleId;
    private BigDecimal exchangeTotal; // Total cost of replacement items
    private BigDecimal priceDifference; // exchangeTotal - refundAmount (positive = customer pays extra)
    private List<ReturnItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
