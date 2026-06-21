package com.lumora.pos.inventory.entity;

import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products", indexes = {
                @Index(name = "idx_products_tenant", columnList = "tenant_id"),
                @Index(name = "idx_products_category", columnList = "category_id"),
                @Index(name = "idx_products_sku", columnList = "sku")
}, uniqueConstraints = {
                @UniqueConstraint(name = "uk_products_sku_tenant", columnNames = { "sku", "tenant_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity extends BaseEntity {

        @Column(nullable = false)
        private String name;

        private String sku;

        private String barcode;

        @Column(columnDefinition = "TEXT")
        private String description;

        @Column(nullable = false)
        private BigDecimal basePrice;

        private BigDecimal costPrice;

        @org.hibernate.annotations.Formula("(SELECT COALESCE(SUM(sl.quantity), 0) FROM stock_levels sl WHERE sl.product_id = id)")
        private Integer stockQuantity;

        @Column(nullable = false)
        private Integer lowStockThreshold;

        private String imageUrl;

        @Builder.Default
        @Column(nullable = false)
        private boolean isActive = true;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "category_id")
        private CategoryEntity category;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "brand_id")
        private BrandEntity brand;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "primary_supplier_id")
        private com.lumora.pos.supplier.entity.SupplierEntity primarySupplier;
}
