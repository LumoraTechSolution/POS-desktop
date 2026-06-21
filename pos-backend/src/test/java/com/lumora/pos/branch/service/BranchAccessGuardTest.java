package com.lumora.pos.branch.service;

import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.superadmin.entity.TenantConfigurationEntity;
import com.lumora.pos.superadmin.repository.TenantConfigurationRepository;
import com.lumora.pos.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BranchAccessGuard Unit Tests")
class BranchAccessGuardTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantConfigurationRepository tenantConfigurationRepository;

    @InjectMocks
    private BranchAccessGuard guard;

    private UUID tenantId;
    private UUID userId;
    private UUID branchA;
    private UUID branchB;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        branchA = UUID.randomUUID();
        branchB = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null,
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private TenantConfigurationEntity config(String... features) {
        TenantConfigurationEntity c = new TenantConfigurationEntity();
        c.setFeaturesEnabled(List.of(features));
        return c;
    }

    @Test
    @DisplayName("Flag OFF: any branch is accessible (no-op)")
    void flagOff_allowsAnyBranch() {
        when(tenantConfigurationRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config("SALES")));

        assertThatCode(() -> guard.assertCanAccess(branchB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Flag ON + ADMIN: any branch is accessible (admins bypass)")
    void flagOn_admin_allowsAnyBranch() {
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(config("BRANCH_RESTRICTIONS")));
        authenticateAs("ROLE_ADMIN");

        assertThatCode(() -> guard.assertCanAccess(branchB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Flag ON + non-admin: assigned branch allowed, others rejected")
    void flagOn_nonAdmin_enforcesAssignment() {
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(config("BRANCH_RESTRICTIONS")));
        when(userRepository.findBranchIdsByUserId(userId)).thenReturn(Set.of(branchA));
        authenticateAs("ROLE_CASHIER");

        assertThatCode(() -> guard.assertCanAccess(branchA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertCanAccess(branchB))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed to operate at this branch");
    }

    @Test
    @DisplayName("Flag ON + non-admin: accessibleBranchIds returns only the assigned set")
    void flagOn_nonAdmin_accessibleBranchIds() {
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(config("BRANCH_RESTRICTIONS")));
        when(userRepository.findBranchIdsByUserId(userId)).thenReturn(Set.of(branchA));
        authenticateAs("ROLE_CASHIER");

        assertThat(guard.accessibleBranchIds()).containsExactly(branchA);
    }
}
