package com.lumora.pos.loyalty.service;

import com.lumora.pos.customer.entity.CustomerEntity;
import com.lumora.pos.customer.repository.CustomerRepository;
import com.lumora.pos.loyalty.dto.LoyaltyConfig;
import com.lumora.pos.loyalty.dto.LoyaltyTransactionResponse;
import com.lumora.pos.loyalty.entity.LoyaltyTransactionEntity;
import com.lumora.pos.loyalty.repository.LoyaltyTransactionRepository;
import com.lumora.pos.tenant.TenantContext;
import com.lumora.pos.tenant.service.TenantInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns the loyalty points ledger: reading a tenant's program config and recording
 * the earn/redeem entries that keep {@code customers.loyalty_points} and the
 * append-only {@link LoyaltyTransactionEntity} history in lock-step.
 *
 * <p>Earn/redeem mutate the {@link CustomerEntity} passed in by the caller (the
 * sale transaction owns it), then append a ledger row capturing the resulting
 * balance. All writes run inside the caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final LoyaltyTransactionRepository ledgerRepository;
    private final CustomerRepository customerRepository;
    private final TenantInfoService tenantInfoService;

    /** The current tenant's loyalty configuration (defaults when never set). */
    @Transactional(readOnly = true)
    public LoyaltyConfig getConfig() {
        return tenantInfoService.getLoyaltyConfig(TenantContext.getTenantId());
    }

    /** Credits points to a customer and records an EARN ledger entry. No-op for non-positive points. */
    @Transactional
    public void recordEarn(CustomerEntity customer, UUID saleId, int points) {
        if (points <= 0) return;
        int newBalance = customer.getLoyaltyPoints() + points;
        customer.setLoyaltyPoints(newBalance);
        customerRepository.save(customer);
        appendLedger(customer.getId(), saleId, LoyaltyTransactionEntity.Type.EARN, points, newBalance,
                "Earned on sale");
    }

    /**
     * Debits points from a customer and records a REDEEM ledger entry. Caller has
     * already validated the balance covers {@code points}; this guards against
     * driving the balance negative as a backstop.
     */
    @Transactional
    public void recordRedeem(CustomerEntity customer, UUID saleId, int points, String description) {
        if (points <= 0) return;
        int newBalance = Math.max(0, customer.getLoyaltyPoints() - points);
        customer.setLoyaltyPoints(newBalance);
        customerRepository.save(customer);
        appendLedger(customer.getId(), saleId, LoyaltyTransactionEntity.Type.REDEEM, -points, newBalance,
                description);
    }

    @Transactional(readOnly = true)
    public Page<LoyaltyTransactionResponse> getLedger(UUID customerId, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        return ledgerRepository
                .findByCustomerIdAndTenantIdOrderByCreatedAtDesc(customerId, tenantId, pageable)
                .map(this::toResponse);
    }

    private void appendLedger(UUID customerId, UUID saleId, LoyaltyTransactionEntity.Type type,
                              int points, int balanceAfter, String description) {
        LoyaltyTransactionEntity tx = new LoyaltyTransactionEntity();
        tx.setTenantId(TenantContext.getTenantId());
        tx.setCustomerId(customerId);
        tx.setSaleId(saleId);
        tx.setType(type);
        tx.setPoints(points);
        tx.setBalanceAfter(balanceAfter);
        tx.setDescription(description);
        ledgerRepository.save(tx);
    }

    private LoyaltyTransactionResponse toResponse(LoyaltyTransactionEntity tx) {
        return LoyaltyTransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType().name())
                .points(tx.getPoints())
                .balanceAfter(tx.getBalanceAfter())
                .description(tx.getDescription())
                .saleId(tx.getSaleId())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
