package com.lumora.pos.auth.entity;

import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity mapping the `permissions` table.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionEntity extends BaseEntity {

    /** e.g., 'SALES_CREATE', 'INVENTORY_EDIT' */
    @Column(nullable = false, length = 100)
    private String name;

    /** e.g., 'SALES', 'INVENTORY', 'REPORTS' */
    @Column(nullable = false, length = 50)
    private String module;

    @Column(length = 255)
    private String description;
}
