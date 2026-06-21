package com.lumora.pos.system.service;

import com.lumora.pos.inventory.repository.ProductRepository;
import com.lumora.pos.system.dto.InventoryHealthResponse;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryHealthService {

    private final ProductRepository productRepository;

    /**
     * Run health check for the current tenant.
     */
    public InventoryHealthResponse checkCurrentTenantHealth() {
        UUID tenantId = TenantContext.getTenantId();
        return performHealthCheck(tenantId);
    }

    /**
     * Automated health check.
     * In a multi-tenant system, this would ideally iterate through all tenants
     * or use a native query to check the whole DB.
     * For now, we'll log summary for current/default context.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void runAutomatedHealthCheck() {
        log.info("Running automated inventory health check...");
        // This is a simplified version. A production system would iterate all tenants.
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            InventoryHealthResponse health = performHealthCheck(tenantId);
            if (!health.isHealthy()) {
                log.error("INVENTORY DISCREPANCY DETECTED! Product Count: {}", health.getDiscrepancyCount());
            } else {
                log.info("Inventory health check passed. Checked {} products.", health.getTotalProductsChecked());
            }
        }
    }

    private InventoryHealthResponse performHealthCheck(UUID tenantId) {
        List<Object[]> results = productRepository.checkInventoryIntegrity(tenantId);
        List<InventoryHealthResponse.ProductDiscrepancy> discrepancies = new ArrayList<>();

        for (Object[] row : results) {
            UUID id = (UUID) row[0];
            String name = (String) row[1];
            Integer globalStock = (Integer) row[2];
            Long branchSum = row[3] != null ? (Long) row[3] : 0L;

            if (globalStock.longValue() != branchSum) {
                discrepancies.add(InventoryHealthResponse.ProductDiscrepancy.builder()
                        .productId(id)
                        .productName(name)
                        .globalStock(globalStock)
                        .calculatedBranchStock(branchSum)
                        .difference(globalStock - branchSum)
                        .build());
            }
        }

        return InventoryHealthResponse.builder()
                .healthy(discrepancies.isEmpty())
                .totalProductsChecked(results.size())
                .discrepancyCount(discrepancies.size())
                .discrepancies(discrepancies)
                .build();
    }
}
