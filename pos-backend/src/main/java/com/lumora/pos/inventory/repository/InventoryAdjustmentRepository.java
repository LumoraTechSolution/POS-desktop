package com.lumora.pos.inventory.repository;

import com.lumora.pos.inventory.entity.InventoryAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustmentEntity, UUID> {
    List<InventoryAdjustmentEntity> findByProductIdOrderByCreatedAtDesc(UUID productId);

    List<InventoryAdjustmentEntity> findByProductIdAndTenantIdOrderByCreatedAtDesc(UUID productId, UUID tenantId);

    List<InventoryAdjustmentEntity> findByBranchIdOrderByCreatedAtDesc(UUID branchId);

    /**
     * Aggregates shrinkage events (RECONCILIATION/DAMAGE/STOCK_OUT) grouped by
     * (productId, type), summing units lost from the {@code quantity} column.
     *
     * Why {@code quantity} instead of {@code previousQuantity - newQuantity}?
     * Damaged-return rows are written with {@code previousQuantity == newQuantity}
     * (stock isn't mutated — the unit was already deducted at the original sale,
     * the row exists purely to record the loss). Using {@code quantity} captures
     * those losses correctly while still matching the delta for stock-mutating
     * adjustments (where {@code quantity = |previous - new|}).
     *
     * RECONCILIATION is filtered to only negative deltas — reconciling stock
     * UPWARD (counted more than expected) is not shrinkage.
     */
    @Query("SELECT ia.product.id, ia.type, SUM(ia.quantity) " +
            "FROM InventoryAdjustmentEntity ia " +
            "WHERE ia.tenantId = :tenantId " +
            "AND ia.createdAt BETWEEN :start AND :end " +
            "AND (" +
            "     ia.type = com.lumora.pos.inventory.entity.InventoryAdjustmentEntity.AdjustmentType.DAMAGE " +
            "  OR ia.type = com.lumora.pos.inventory.entity.InventoryAdjustmentEntity.AdjustmentType.STOCK_OUT " +
            "  OR (ia.type = com.lumora.pos.inventory.entity.InventoryAdjustmentEntity.AdjustmentType.RECONCILIATION " +
            "      AND ia.newQuantity < ia.previousQuantity)" +
            ") " +
            "GROUP BY ia.product.id, ia.type")
    List<Object[]> aggregateShrinkageByProductAndType(@Param("tenantId") UUID tenantId,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    @Query("SELECT ia.product.id, ia.type, SUM(ia.quantity) " +
            "FROM InventoryAdjustmentEntity ia " +
            "WHERE ia.tenantId = :tenantId " +
            "AND ia.createdAt BETWEEN :start AND :end " +
            "AND ia.branch.id IN :branchIds " +
            "AND (" +
            "     ia.type = com.lumora.pos.inventory.entity.InventoryAdjustmentEntity.AdjustmentType.DAMAGE " +
            "  OR ia.type = com.lumora.pos.inventory.entity.InventoryAdjustmentEntity.AdjustmentType.STOCK_OUT " +
            "  OR (ia.type = com.lumora.pos.inventory.entity.InventoryAdjustmentEntity.AdjustmentType.RECONCILIATION " +
            "      AND ia.newQuantity < ia.previousQuantity)" +
            ") " +
            "GROUP BY ia.product.id, ia.type")
    List<Object[]> aggregateShrinkageByProductAndTypeAndBranch(@Param("tenantId") UUID tenantId,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      @Param("branchIds") java.util.Collection<UUID> branchIds);
}
