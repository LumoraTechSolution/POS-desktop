package com.lumora.pos.branch.service;

import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.superadmin.repository.TenantConfigurationRepository;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single source of truth for "which branches may the current user operate at".
 *
 * Gated by the {@code BRANCH_RESTRICTIONS} feature flag so the whole mechanism is opt-in
 * per tenant: when the flag is OFF (or the caller is an ADMIN) every tenant branch is
 * accessible, exactly as before this feature existed. When ON, non-admin users are limited
 * to the branches assigned to them via {@code user_branches}.
 *
 * Reads the current principal from the {@link SecurityContextHolder} (userId is the
 * authentication name; roles are the granted authorities) — the same convention used by
 * SaleService and the audit services.
 */
@Component
@RequiredArgsConstructor
public class BranchAccessGuard {

    private static final String FEATURE = "BRANCH_RESTRICTIONS";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final TenantConfigurationRepository tenantConfigurationRepository;

    /** True when the current tenant has branch restrictions enabled. */
    @Transactional(readOnly = true)
    public boolean restrictionsActive(UUID tenantId) {
        return tenantConfigurationRepository.findByTenantId(tenantId)
                .map(c -> c.hasFeature(FEATURE))
                .orElse(false);
    }

    /**
     * Branch ids the current user may operate at. Returns all tenant branches when
     * restrictions are off or the caller is an admin / has no resolvable user id.
     */
    @Transactional(readOnly = true)
    public Set<UUID> accessibleBranchIds() {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = currentUserId();

        if (!restrictionsActive(tenantId) || isAdmin() || userId == null) {
            return allTenantBranchIds(tenantId);
        }
        return userRepository.findBranchIdsByUserId(userId);
    }

    /**
     * Throws if the current user may not operate at {@code branchId}. No-op when
     * restrictions are off or the caller is an admin.
     */
    @Transactional(readOnly = true)
    public void assertCanAccess(UUID branchId) {
        UUID tenantId = TenantContext.getTenantId();
        if (!restrictionsActive(tenantId) || isAdmin()) {
            return;
        }
        if (branchId == null || !accessibleBranchIds().contains(branchId)) {
            throw new BusinessException("You are not allowed to operate at this branch");
        }
    }

    /**
     * Resolves the branch filter for a report/finance endpoint.
     * <ul>
     *   <li>Explicit {@code branchId} → that single branch (after an access check).</li>
     *   <li>No param, restrictions off or caller is admin → {@code empty} = no filter,
     *       the all-branch aggregate (status quo, leaves the existing query untouched).</li>
     *   <li>No param, restrictions on and caller is non-admin → their accessible branches.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Optional<Set<UUID>> reportBranchFilter(UUID branchId) {
        UUID tenantId = TenantContext.getTenantId();
        if (branchId != null) {
            assertCanAccess(branchId);
            return Optional.of(Set.of(branchId));
        }
        if (!restrictionsActive(tenantId) || isAdmin()) {
            return Optional.empty();
        }
        return Optional.of(accessibleBranchIds());
    }

    private Set<UUID> allTenantBranchIds(UUID tenantId) {
        return branchRepository.findAllByTenantId(tenantId).stream()
                .map(BranchEntity::getId)
                .collect(Collectors.toSet());
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
