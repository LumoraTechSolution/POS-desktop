package com.lumora.pos.inventory.entity;

import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "inventory_adjustments")
@Getter
@Setter
public class InventoryAdjustmentEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private BranchEntity branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false)
    private AdjustmentType type;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "previous_quantity", nullable = false)
    private Integer previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private Integer newQuantity;

    @Column(name = "reason")
    private String reason;

    @Column(name = "reference_id")
    private String referenceId; // e.g., Purchase Order ID, Sale ID, or Transfer ID

    public enum AdjustmentType {
        STOCK_IN, // General addition
        STOCK_OUT, // General deduction
        SALE, // Sold via POS
        RETURN, // Customer return
        RECONCILIATION, // Manual correction/Stock take
        DAMAGE, // Expired or broken
        TRANSFER_IN, // Received from another branch
        TRANSFER_OUT // Sent to another branch
    }
}
