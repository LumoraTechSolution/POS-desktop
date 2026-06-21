package com.lumora.pos.loyalty.repository;

import com.lumora.pos.loyalty.entity.LoyaltyTransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransactionEntity, UUID> {

    Page<LoyaltyTransactionEntity> findByCustomerIdAndTenantIdOrderByCreatedAtDesc(
            UUID customerId, UUID tenantId, Pageable pageable);
}
