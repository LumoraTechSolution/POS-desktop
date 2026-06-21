package com.lumora.pos.tax.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TaxRateResponse {
    private UUID id;
    private String name;
    private BigDecimal rate; // Stored as decimal (e.g. 0.1000)
    private BigDecimal ratePercent; // Human-friendly (e.g. 10.00)
    private String description;

    @JsonProperty("isDefault")
    private boolean isDefault;

    @JsonProperty("isActive")
    private boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
