package com.lumora.pos.expense.entity;

import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses", indexes = {
        @Index(name = "idx_expenses_tenant_date", columnList = "tenant_id, expense_date"),
        @Index(name = "idx_expenses_tenant_category", columnList = "tenant_id, category_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategoryEntity category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(length = 255)
    private String payee;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(length = 100)
    private String reference;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private boolean recurring = false;

    @Column(name = "recurring_interval", length = 20)
    private String recurringInterval;

    /** Optional branch this cost belongs to. Null = company-wide overhead (not attributed
     *  to a single location) — used by branch-scoped P&L / cash-flow. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private BranchEntity branch;
}
