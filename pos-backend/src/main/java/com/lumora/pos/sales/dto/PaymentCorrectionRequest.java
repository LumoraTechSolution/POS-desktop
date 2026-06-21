package com.lumora.pos.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload for {@code PATCH /api/v1/sales/{id}/payment-correction}.
 * Either {@link #paymentMethod} or {@link #cashTendered} (or both) must be
 * provided; the service rejects the call otherwise. {@code managerPin} is
 * required when the actor is outside the cashier self-serve window
 * (sale not theirs, or sale older than the configured window).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCorrectionRequest {

    /** CASH, CARD, ONLINE, SPLIT, or CREDIT. Optional. */
    private String paymentMethod;

    /** New cash tendered figure. Optional. Ignored for CARD/ONLINE/CREDIT. */
    @DecimalMin(value = "0", message = "cashTendered must be non-negative")
    private BigDecimal cashTendered;

    /** Plaintext manager PIN, required outside the cashier self-serve window. */
    private String managerPin;
}
