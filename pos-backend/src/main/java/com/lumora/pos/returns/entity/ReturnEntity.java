package com.lumora.pos.returns.entity;

import com.lumora.pos.common.entity.BaseEntity;
import com.lumora.pos.sales.entity.SaleEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Where;

@Entity
@Table(name = "returns")
@Getter
@Setter
@Where(clause = "is_deleted = false")
public class ReturnEntity extends BaseEntity {

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private SaleEntity sale;

    @Column(name = "return_number", nullable = false, length = 50)
    private String returnNumber;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false, length = 20)
    private ReturnType returnType = ReturnType.REFUND;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.PENDING;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false, length = 20)
    private RefundMethod refundMethod = RefundMethod.ORIGINAL;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // For exchange returns: references the new replacement sale
    @Column(name = "exchange_sale_id")
    private UUID exchangeSaleId;

    @OneToMany(mappedBy = "returnEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReturnItemEntity> items = new ArrayList<>();

    // ─── Enums ──────────────────────────────────────────────

    public enum ReturnType {
        REFUND, // Normal return — refund money + restore stock
        EXCHANGE, // Item swap — restore returned stock, deduct new stock, create new sale
        DAMAGED_WRITEOFF // Defective/damaged — refund money but DO NOT restore stock
    }

    public enum ReturnStatus {
        PENDING, // Awaiting manager approval
        APPROVED, // Manager approved, ready to process
        COMPLETED, // Refund processed
        REJECTED // Manager rejected the return
    }

    public enum RefundMethod {
        ORIGINAL, // Refund to original payment method
        CASH, // Cash refund
        STORE_CREDIT // Credit to customer account
    }
}
