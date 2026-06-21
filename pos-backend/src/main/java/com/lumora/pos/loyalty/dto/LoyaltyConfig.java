package com.lumora.pos.loyalty.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A tenant's loyalty program settings, read from the tenant {@code settings} JSONB.
 *
 * <ul>
 *   <li>{@code spendPerPoint} — currency a customer must spend to earn 1 point
 *       (default 10 → 1 point per LKR 10).</li>
 *   <li>{@code pointValue} — currency value of 1 point when redeemed
 *       (default 0.10 → 100 points = LKR 10 off).</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
public class LoyaltyConfig {

    public static final BigDecimal DEFAULT_SPEND_PER_POINT = new BigDecimal("10");
    public static final BigDecimal DEFAULT_POINT_VALUE = new BigDecimal("0.10");

    private boolean enabled;
    private BigDecimal spendPerPoint;
    private BigDecimal pointValue;

    /** Sensible defaults used when a tenant has never configured loyalty. */
    public static LoyaltyConfig defaults() {
        return LoyaltyConfig.builder()
                .enabled(true)
                .spendPerPoint(DEFAULT_SPEND_PER_POINT)
                .pointValue(DEFAULT_POINT_VALUE)
                .build();
    }

    /** Points earned for a given paid amount: floor(amount / spendPerPoint). */
    public int pointsForSpend(BigDecimal amount) {
        if (!enabled || amount == null || spendPerPoint == null
                || spendPerPoint.signum() <= 0 || amount.signum() <= 0) {
            return 0;
        }
        return amount.divide(spendPerPoint, 0, RoundingMode.DOWN).intValue();
    }

    /** Cash value of redeeming the given number of points. */
    public BigDecimal valueOfPoints(int points) {
        if (pointValue == null || points <= 0) return BigDecimal.ZERO;
        return pointValue.multiply(BigDecimal.valueOf(points)).setScale(2, RoundingMode.HALF_UP);
    }
}
