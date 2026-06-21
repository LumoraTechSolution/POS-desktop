package com.lumora.pos.loyalty.entity;

import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * One append-only entry in a customer's loyalty points ledger.
 *
 * <p>The signed {@link #points} (positive = earned, negative = redeemed) plus the
 * {@link #balanceAfter} snapshot make the customer's history auditable without
 * having to replay every sale. The single source of truth for the current balance
 * remains {@code customers.loyalty_points}; each ledger row records the value it
 * resulted in.
 */
@Entity
@Table(name = "loyalty_transactions")
@Getter
@Setter
public class LoyaltyTransactionEntity extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** The sale that earned/spent these points. Null for manual adjustments. */
    @Column(name = "sale_id")
    private UUID saleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    /** Signed points delta — positive for EARN, negative for REDEEM. */
    @Column(name = "points", nullable = false)
    private int points;

    /** Customer's points balance immediately after this entry was applied. */
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "description", length = 255)
    private String description;

    public enum Type {
        EARN, REDEEM, ADJUST
    }
}
