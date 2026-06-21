package com.lumora.pos.branch.entity;

import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "branches", indexes = {
        @Index(name = "idx_branches_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String address;

    private String phoneNumber;

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean isDefault = false;
}
