package com.lumora.pos.purchase.repository;

import com.lumora.pos.purchase.entity.PurchaseOrderEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, UUID>,
        JpaSpecificationExecutor<PurchaseOrderEntity> {

    Page<PurchaseOrderEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    Page<PurchaseOrderEntity> findAllByTenantIdAndSupplierId(UUID tenantId, UUID supplierId, Pageable pageable);

    Page<PurchaseOrderEntity> findAllByTenantIdAndBranchId(UUID tenantId, UUID branchId, Pageable pageable);

    Optional<PurchaseOrderEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Total inventory spend (PO value) for a status within a created-at range — a cash outflow for cash-flow reporting. */
    @Query("""
            SELECT COALESCE(SUM(po.totalAmount), 0) FROM PurchaseOrderEntity po
            WHERE po.tenantId = :tenantId AND po.status = :status AND po.createdAt BETWEEN :start AND :end
            """)
    BigDecimal sumByStatusAndPeriod(@Param("tenantId") UUID tenantId,
                                    @Param("status") PurchaseOrderEntity.POStatus status,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /** Branch-scoped variant of {@link #sumByStatusAndPeriod} for per-branch cash-flow. */
    @Query("""
            SELECT COALESCE(SUM(po.totalAmount), 0) FROM PurchaseOrderEntity po
            WHERE po.tenantId = :tenantId AND po.status = :status AND po.createdAt BETWEEN :start AND :end
            AND po.branch.id IN :branchIds
            """)
    BigDecimal sumByStatusAndPeriodAndBranch(@Param("tenantId") UUID tenantId,
                                    @Param("status") PurchaseOrderEntity.POStatus status,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("branchIds") java.util.Collection<UUID> branchIds);

    /**
     * Composable filter spec for the purchase-orders list endpoint. We use
     * Specifications instead of a single {@code @Query} with {@code IS NULL OR}
     * predicates because Hibernate 6 + the PostgreSQL JDBC driver can't infer
     * the bind type for NULL parameters that only appear in {@code IS NULL}
     * checks ("could not determine data type of parameter"). With Specifications
     * we conditionally add predicates only when a filter is actually set — no
     * NULL parameters are ever bound.
     */
    static Specification<PurchaseOrderEntity> filtered(UUID tenantId,
                                                       PurchaseOrderEntity.POStatus status,
                                                       UUID supplierId,
                                                       String search) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("tenantId"), tenantId));
            if (status != null) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (supplierId != null) {
                preds.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }
            if (search != null && !search.isBlank()) {
                preds.add(cb.like(cb.lower(root.get("poNumber")), "%" + search.toLowerCase() + "%"));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
