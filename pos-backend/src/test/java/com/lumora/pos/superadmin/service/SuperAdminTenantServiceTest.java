package com.lumora.pos.superadmin.service;

import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.auth.repository.RoleRepository;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.common.exception.ResourceNotFoundException;
import com.lumora.pos.config.BillingProperties;
import com.lumora.pos.superadmin.dto.TenantConfigurationRequest;
import com.lumora.pos.superadmin.entity.TenantConfigurationEntity;
import com.lumora.pos.superadmin.entity.TenantEntity;
import com.lumora.pos.superadmin.enums.PlanTier;
import com.lumora.pos.superadmin.repository.TenantConfigurationRepository;
import com.lumora.pos.superadmin.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminTenantService Unit Tests")
class SuperAdminTenantServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantConfigurationRepository tenantConfigurationRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SuperAdminAuditService auditService;
    @Mock private BillingProperties billingProperties;

    @InjectMocks private SuperAdminTenantService tenantService;

    private UUID tenantId;
    private TenantEntity tenant;
    private TenantConfigurationEntity config;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenant = TenantEntity.builder()
                .id(tenantId)
                .name("Acme")
                .domain("acme")
                .isActive(true)
                .build();
        config = TenantConfigurationEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .planTier(PlanTier.SMALL_BUSINESS)
                .maxLocations(1)
                .maxUsers(5)
                .maxProducts(500)
                .featuresEnabled(List.of("SALES"))
                .isActive(true)
                .build();
    }

    /** Common stubs used by service methods that re-fetch via getTenantDetail. */
    private void stubDetailReadsForActive() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantConfigurationRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
        // JdbcTemplate counts inside getTenantDetail — stubs are lenient so each
        // test doesn't have to wire all of them when verifying behavior.
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(0L);
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(java.math.BigDecimal.class), any()))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("suspendTenant flips isActive=false on config + tenant and audits TENANT_SUSPENDED")
    void suspendTenant_audits() {
        stubDetailReadsForActive();

        tenantService.suspendTenant(tenantId);

        assertThat(config.isActive()).isFalse();
        assertThat(tenant.isActive()).isFalse();
        verify(tenantConfigurationRepository).save(config);
        verify(tenantRepository).save(tenant);
        verify(auditService).logTenantMutation(eq(AuditAction.TENANT_SUSPENDED), eq(tenantId), any(), any());
    }

    @Test
    @DisplayName("activateTenant flips isActive=true on config + tenant and audits TENANT_REACTIVATED")
    void activateTenant_audits() {
        config.setActive(false);
        tenant.setActive(false);
        stubDetailReadsForActive();

        tenantService.activateTenant(tenantId);

        assertThat(config.isActive()).isTrue();
        assertThat(tenant.isActive()).isTrue();
        verify(auditService).logTenantMutation(eq(AuditAction.TENANT_REACTIVATED), eq(tenantId), any(), any());
    }

    @Test
    @DisplayName("updateTenantConfiguration writes new values and audits TENANT_CONFIG_UPDATED")
    void updateTenantConfiguration_audits() {
        stubDetailReadsForActive();

        TenantConfigurationRequest req = new TenantConfigurationRequest();
        req.setPlanTier(PlanTier.ENTERPRISE);
        req.setMaxLocations(999);
        req.setMaxUsers(999);
        req.setMaxProducts(999_999);
        req.setFeaturesEnabled(List.of("SALES", "INVENTORY", "API_ACCESS"));
        req.setActive(true);
        req.setNotes("upgraded");

        tenantService.updateTenantConfiguration(tenantId, req);

        assertThat(config.getPlanTier()).isEqualTo(PlanTier.ENTERPRISE);
        assertThat(config.getMaxLocations()).isEqualTo(999);
        verify(tenantConfigurationRepository).save(config);
        verify(auditService).logTenantMutation(eq(AuditAction.TENANT_CONFIG_UPDATED), eq(tenantId), any(), any());
    }

    @Test
    @DisplayName("updateTenantConfiguration on missing config throws ResourceNotFoundException")
    void updateTenantConfiguration_missingConfig_throws() {
        when(tenantConfigurationRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        TenantConfigurationRequest req = new TenantConfigurationRequest();
        req.setPlanTier(PlanTier.SMALL_BUSINESS);

        assertThatThrownBy(() -> tenantService.updateTenantConfiguration(tenantId, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getPlatformStats projects MRR from BillingProperties, not magic numbers")
    void getPlatformStats_mrrFromBillingProperties() {
        when(tenantRepository.count()).thenReturn(10L);
        when(tenantConfigurationRepository.countByIsActive(true)).thenReturn(8L);
        when(tenantConfigurationRepository.countByIsActive(false)).thenReturn(2L);
        when(tenantConfigurationRepository.countByPlanTier(PlanTier.SMALL_BUSINESS)).thenReturn(5L);
        when(tenantConfigurationRepository.countByPlanTier(PlanTier.MEDIUM_BUSINESS)).thenReturn(3L);
        when(tenantConfigurationRepository.countByPlanTier(PlanTier.ENTERPRISE)).thenReturn(2L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        when(billingProperties.priceFor(PlanTier.SMALL_BUSINESS)).thenReturn(new BigDecimal("100"));
        when(billingProperties.priceFor(PlanTier.MEDIUM_BUSINESS)).thenReturn(new BigDecimal("200"));
        when(billingProperties.priceFor(PlanTier.ENTERPRISE)).thenReturn(new BigDecimal("400"));

        // 5*100 + 3*200 + 2*400 = 1900
        assertThat(tenantService.getPlatformStats().getProjectedMrr())
                .isEqualByComparingTo(new BigDecimal("1900"));
    }
}
