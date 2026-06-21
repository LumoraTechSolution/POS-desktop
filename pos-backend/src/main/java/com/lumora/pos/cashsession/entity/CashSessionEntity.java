package com.lumora.pos.cashsession.entity;

import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.common.entity.BaseEntity;
import com.lumora.pos.employee.entity.TimeRecord;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_sessions")
@Getter
@Setter
public class CashSessionEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_record_id", nullable = false, unique = true)
    private TimeRecord timeRecord;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Branch this drawer was opened at. Nullable for legacy rows backfilled to the default branch. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private BranchEntity branch;

    @Column(name = "opening_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 15, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "expected_balance", precision = 15, scale = 2)
    private BigDecimal expectedBalance;

    @Column(name = "variance", precision = 15, scale = 2)
    private BigDecimal variance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public enum Status {
        OPEN, CLOSED
    }
}
