package com.lumora.pos.user.service;

import com.lumora.pos.auth.entity.UserEntity;
import com.lumora.pos.auth.repository.RefreshTokenRepository;
import com.lumora.pos.auth.repository.RoleRepository;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.cashsession.repository.CashSessionRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.superadmin.repository.TenantConfigurationRepository;
import com.lumora.pos.tenant.TenantContext;
import com.lumora.pos.user.dto.UserManagementDtos.UpdateUserRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserManagementService — PIN uniqueness")
class UserManagementServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private BranchRepository branchRepository;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private TenantConfigurationRepository tenantConfigurationRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private com.lumora.pos.auth.service.PinLookupHasher pinLookupHasher;

    @InjectMocks
    private UserManagementService service;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Rejects a PIN already used by another user in the business")
    void shouldRejectDuplicatePin() {
        UUID targetId = UUID.randomUUID();
        UserEntity target = UserEntity.builder().email("target@example.com")
                .firstName("T").lastName("U").pin("target-hash").build();
        target.setId(targetId);
        target.setTenantId(tenantId);

        UserEntity other = UserEntity.builder().email("other@example.com")
                .firstName("O").lastName("U").pin("other-hash").build();
        other.setId(UUID.randomUUID());
        other.setTenantId(tenantId);

        when(userRepository.findByIdAndTenantId(targetId, tenantId)).thenReturn(Optional.of(target));
        when(userRepository.findAllWithPinByTenantId(tenantId)).thenReturn(List.of(target, other));
        when(passwordEncoder.matches("1234", "other-hash")).thenReturn(true);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setPin("1234");

        assertThatThrownBy(() -> service.updateUser(targetId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("findPinConflicts groups users that share a blind-index lookup")
    void shouldReportPinConflicts() {
        UserEntity a = userWithLookup("Ann", "L1");
        UserEntity b = userWithLookup("Bob", "L1");
        UserEntity c = userWithLookup("Cal", "L2");
        UserEntity legacy = userWithLookup("Dot", null); // pre-V54 PIN, not grouped

        when(userRepository.findAllWithPinByTenantId(tenantId))
                .thenReturn(List.of(a, b, c, legacy));

        var conflicts = service.findPinConflicts();

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getUsers()).hasSize(2);
        assertThat(conflicts.get(0).getUsers())
                .extracting(u -> u.getFirstName())
                .containsExactlyInAnyOrder("Ann", "Bob");
    }

    private UserEntity userWithLookup(String firstName, String lookup) {
        UserEntity u = UserEntity.builder()
                .email(firstName.toLowerCase() + "@example.com")
                .firstName(firstName).lastName("U").pin("hash").pinLookup(lookup).build();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        return u;
    }
}
