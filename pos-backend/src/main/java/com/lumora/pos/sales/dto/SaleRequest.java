package com.lumora.pos.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SaleRequest {
    private UUID customerId;
    private UUID branchId;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

    /** Cash tendered by the customer. Required for SPLIT payments; ignored for
     *  CARD/ONLINE (service defaults to 0). For pure CASH sales the service
     *  always stores netAmount — the client value is UI-only (change display). */
    @DecimalMin(value = "0", message = "cashTendered must be non-negative")
    private BigDecimal cashTendered;

    /** Loyalty points the customer wants to redeem on this sale. Requires a
     *  customerId; the service caps it to the balance and the bill total and
     *  recomputes the discount authoritatively. Optional / 0 = none. */
    @jakarta.validation.constraints.Min(value = 0, message = "pointsToRedeem must be non-negative")
    private Integer pointsToRedeem;

    @NotEmpty(message = "Sale must contain at least one item")
    @Valid
    private List<SaleItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaleItemRequest {
        /** Catalog product. Null for a custom/open line (then itemName is required). */
        private UUID productId;

        /** Typed name for a custom/open line. Ignored when productId is set. */
        @Size(max = 255)
        private String itemName;

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0", inclusive = false, message = "quantity must be positive")
        private BigDecimal quantity;

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0", message = "unitPrice must be non-negative")
        private BigDecimal unitPrice;

        @DecimalMin(value = "0", message = "discountAmount must be non-negative")
        private BigDecimal discountAmount;

        /** A line is valid if it references a catalog product OR carries a custom name. */
        @AssertTrue(message = "Each line must have a productId or a custom itemName")
        public boolean isProductOrName() {
            return productId != null || (itemName != null && !itemName.isBlank());
        }
    }
}
