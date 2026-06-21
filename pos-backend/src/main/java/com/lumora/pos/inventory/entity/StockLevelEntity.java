package com.lumora.pos.inventory.entity;

import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "stock_levels", indexes = {
        @Index(name = "idx_stock_levels_tenant", columnList = "tenant_id"),
        @Index(name = "idx_stock_levels_product", columnList = "product_id"),
        @Index(name = "idx_stock_levels_branch", columnList = "branch_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_levels_product_branch", columnNames = { "product_id", "branch_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLevelEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private BranchEntity branch;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;
}
