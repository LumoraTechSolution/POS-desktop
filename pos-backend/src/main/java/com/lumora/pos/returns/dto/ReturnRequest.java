package com.lumora.pos.returns.dto;

import com.lumora.pos.returns.entity.ReturnEntity.RefundMethod;
import com.lumora.pos.returns.entity.ReturnEntity.ReturnType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequest {
    @NotNull(message = "Sale ID is required")
    private UUID saleId;

    @NotBlank(message = "Reason is required")
    private String reason;

    private ReturnType returnType;

    @NotNull(message = "Refund method is required")
    private RefundMethod refundMethod;

    private String notes;

    @NotEmpty(message = "At least one item must be returned")
    @Valid
    private List<ReturnItemRequest> items;

    // Only used for EXCHANGE type returns
    @Valid
    private List<ExchangeItemRequest> exchangeItems;
}
