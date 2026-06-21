package com.lumora.pos.expense.repository;

import com.lumora.pos.expense.entity.ExpenseCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, UUID> {

    List<ExpenseCategoryEntity> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<ExpenseCategoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    long countByTenantId(UUID tenantId);
}
