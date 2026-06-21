package com.lumora.pos.loyalty.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LoyaltyTransactionResponse {
    private UUID id;
    /** EARN, REDEEM or ADJUST. */
    private String type;
    /** Signed points delta (positive earned, negative redeemed). */
    private int points;
    /** Running balance after this entry. */
    private int balanceAfter;
    private String description;
    private UUID saleId;
    private LocalDateTime createdAt;
}
