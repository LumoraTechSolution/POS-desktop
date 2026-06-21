package com.lumora.pos.config;

import com.lumora.pos.superadmin.enums.PlanTier;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Plan pricing for the MRR projection on the super-admin dashboard.
 * Bound from {@code app.billing.plan-prices.*} so finance can adjust
 * the model without a code deploy.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.billing")
public class BillingProperties {

    /** Monthly price (in the dashboard's display currency) per plan tier. */
    private Map<PlanTier, BigDecimal> planPrices = new EnumMap<>(PlanTier.class);

    /**
     * Resolves a tier's monthly price, falling back to zero so a freshly
     * added tier without a configured price doesn't blow up the dashboard.
     */
    public BigDecimal priceFor(PlanTier tier) {
        BigDecimal v = planPrices.get(tier);
        return v != null ? v : BigDecimal.ZERO;
    }
}
