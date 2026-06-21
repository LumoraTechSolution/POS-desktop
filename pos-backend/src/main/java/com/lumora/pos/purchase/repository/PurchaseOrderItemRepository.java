package com.lumora.pos.purchase.repository;

import com.lumora.pos.purchase.entity.PurchaseOrderEntity;
import com.lumora.pos.purchase.entity.PurchaseOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItemEntity, UUID> {

    /**
     * Returns (productId, supplierId, supplierName, supplierActive) rows for the given
     * product set, ordered by PO creation time DESC. Callers can iterate and take the
     * first row per product to resolve the most recent supplier.
     * Excludes DRAFT and CANCELLED POs — those aren't real sourcing events.
     */
    @Query("SELECT poi.product.id, po.supplier.id, po.supplier.name, po.supplier.isActive " +
           "FROM PurchaseOrderItemEntity poi JOIN poi.purchaseOrder po " +
           "WHERE po.tenantId = :tenantId " +
           "AND po.status IN :statuses " +
           "AND poi.product.id IN :productIds " +
           "ORDER BY po.createdAt DESC")
    List<Object[]> findLatestSupplierForProducts(@Param("tenantId") UUID tenantId,
                                                 @Param("productIds") Collection<UUID> productIds,
                                                 @Param("statuses") Collection<PurchaseOrderEntity.POStatus> statuses);
}
