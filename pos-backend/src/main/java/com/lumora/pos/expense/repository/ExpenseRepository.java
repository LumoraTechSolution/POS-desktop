package com.lumora.pos.expense.repository;

import com.lumora.pos.expense.entity.ExpenseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends org.springframework.data.jpa.repository.JpaRepository<ExpenseEntity, UUID> {

    Page<ExpenseEntity> findAllByTenantIdOrderByExpenseDateDesc(UUID tenantId, Pageable pageable);

    Page<ExpenseEntity> findAllByTenantIdAndExpenseDateBetweenOrderByExpenseDateDesc(
            UUID tenantId, LocalDate start, LocalDate end, Pageable pageable);

    Page<ExpenseEntity> findAllByTenantIdAndBranch_IdInOrderByExpenseDateDesc(
            UUID tenantId, Collection<UUID> branchIds, Pageable pageable);

    Page<ExpenseEntity> findAllByTenantIdAndBranch_IdInAndExpenseDateBetweenOrderByExpenseDateDesc(
            UUID tenantId, Collection<UUID> branchIds, LocalDate start, LocalDate end, Pageable pageable);

    Optional<ExpenseEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByCategory_IdAndTenantId(UUID categoryId, UUID tenantId);

    /** Total operating expenses in the (inclusive) date range. */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM ExpenseEntity e
            WHERE e.tenantId = :tenantId AND e.expenseDate BETWEEN :start AND :end
            """)
    BigDecimal sumByPeriod(@Param("tenantId") UUID tenantId,
                           @Param("start") LocalDate start,
                           @Param("end") LocalDate end);

    /** Branch-scoped operating expenses. Company-overhead (null branch) rows are excluded. */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM ExpenseEntity e
            WHERE e.tenantId = :tenantId AND e.expenseDate BETWEEN :start AND :end
            AND e.branch.id IN :branchIds
            """)
    BigDecimal sumByPeriodAndBranch(@Param("tenantId") UUID tenantId,
                           @Param("start") LocalDate start,
                           @Param("end") LocalDate end,
                           @Param("branchIds") Collection<UUID> branchIds);

    /** [categoryId, categoryName, total] grouped by category for the range. */
    @Query("""
            SELECT e.category.id, e.category.name, COALESCE(SUM(e.amount), 0) FROM ExpenseEntity e
            WHERE e.tenantId = :tenantId AND e.expenseDate BETWEEN :start AND :end
            GROUP BY e.category.id, e.category.name
            ORDER BY SUM(e.amount) DESC
            """)
    List<Object[]> sumByCategory(@Param("tenantId") UUID tenantId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);

    /** Branch-scoped {@link #sumByCategory}. Company-overhead (null branch) rows are excluded. */
    @Query("""
            SELECT e.category.id, e.category.name, COALESCE(SUM(e.amount), 0) FROM ExpenseEntity e
            WHERE e.tenantId = :tenantId AND e.expenseDate BETWEEN :start AND :end
            AND e.branch.id IN :branchIds
            GROUP BY e.category.id, e.category.name
            ORDER BY SUM(e.amount) DESC
            """)
    List<Object[]> sumByCategoryAndBranch(@Param("tenantId") UUID tenantId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end,
                                 @Param("branchIds") Collection<UUID> branchIds);
}
