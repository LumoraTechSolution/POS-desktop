package com.lumora.pos.tax.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRateRequest {

    @NotBlank(message = "Tax name is required")
    private String name;

    @NotNull(message = "Tax rate is required")
    @PositiveOrZero(message = "Tax rate cannot be negative")
    private BigDecimal rate; // As percentage, e.g. 10 for 10%. Service converts to 0.10.

    private String description;

    @JsonProperty("isDefault")
    private boolean isDefault;

    @JsonProperty("isActive")
    @Builder.Default
    private boolean isActive = true;
}
