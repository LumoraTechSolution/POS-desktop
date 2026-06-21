package com.lumora.pos.auth.service;

import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.audit.service.AuditService;
import com.lumora.pos.auth.dto.AuthResponse;
import com.lumora.pos.auth.dto.LoginRequest;
import com.lumora.pos.auth.dto.RefreshTokenRequest;
import com.lumora.pos.auth.entity.RefreshTokenEntity;
import com.lumora.pos.auth.entity.RoleEntity;
import com.lumora.pos.auth.entity.UserEntity;
import com.lumora.pos.auth.repository.RefreshTokenRepository;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.config.JwtProperties;
import com.lumora.pos.config.JwtTokenProvider;
import com.lumora.pos.superadmin.entity.TenantConfigurationEntity;
import com.lumora.pos.superadmin.entity.TenantEntity;
import com.lumora.pos.superadmin.enums.PlanTier;
import com.lumora.pos.superadmin.repository.TenantConfigurationRepository;
import com.lumora.pos.superadmin.repository.TenantRepository;
import com.lumora.pos.auth.dto.PinLoginRequest;
import com.lumora.pos.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private AuditService auditService;
    @Mock
    private TenantConfigurationRepository tenantConfigurationRepository;
    @Mock
    private com.lumora.pos.cashsession.service.CashSessionService cashSessionService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private PinLookupHasher pinLookupHasher;

    @InjectMocks
    private AuthService authService;

    private UUID tenantId;
    private UUID userId;
    private UserEntity userEntity;
    private TenantConfigurationEntity tenantConfig;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        userEntity = UserEntity.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .isActive(true)
                .roles(Collections.emptySet())
                .build();
        userEntity.setId(userId);
        userEntity.setTenantId(tenantId);

        tenantConfig = TenantConfigurationEntity.builder()
                .tenantId(tenantId)
                .isActive(true)
                .planTier(PlanTier.SMALL_BUSINESS)
                .build();

        UserDetails userDetails = new User(userEntity.getEmail(), "dummy", Collections.emptyList());
        authentication = new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should successfully login an active user")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(userEntity));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userDetailsService.loadUserByUsername("test@example.com"))
                .thenReturn(new User(userEntity.getEmail(), "dummy", Collections.emptyList()));
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(tenantConfig));

        when(jwtTokenProvider.generateAccessToken(any(), eq(userId), eq(tenantId)))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken())
                .thenReturn("refresh-token");
        when(jwtProperties.getRefreshExpirationMs())
                .thenReturn(10000L);

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        
        verify(auditService, times(1)).logAuthEvent(eq(AuditAction.LOGIN), eq(userId), any());
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Should fail when credentials are bad")
    void shouldFailOnBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongpassword");

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(userEntity));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad creds"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
        
        verify(auditService, times(1)).logAuthEvent(eq(AuditAction.LOGIN_FAILED), isNull(), any());
    }

    @Test
    @DisplayName("Should fail login if tenant is suspended")
    void shouldFailIfTenantSuspended() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        tenantConfig.setActive(false);

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(userEntity));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(tenantConfig));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    @DisplayName("Should fail login if user account is inactive")
    void shouldFailIfUserIsInactive() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        userEntity.setActive(false);

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(userEntity));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(tenantConfig));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    @DisplayName("Should fail login if subscription has expired")
    void shouldFailIfSubscriptionExpired() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        tenantConfig.setSubscriptionEnd(LocalDateTime.now().minusDays(1));

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(userEntity));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(tenantConfig));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("PIN login resolves the single tenant and the matching user")
    void shouldPinLoginAgainstSingleTenant() {
        userEntity.setPin("hashed-pin");

        TenantEntity tenant = TenantEntity.builder().name("Acme").isActive(true).build();
        tenant.setId(tenantId);

        when(tenantRepository.findByIsActiveTrue()).thenReturn(java.util.List.of(tenant));
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(tenantConfig));
        when(userRepository.findActiveUsersWithPinByTenantId(tenantId))
                .thenReturn(java.util.List.of(userEntity));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(pinLookupHasher.hash("1234")).thenReturn("lookup-1234");
        when(userDetailsService.loadUserByUsername("test@example.com"))
                .thenReturn(new User(userEntity.getEmail(), "dummy", Collections.emptyList()));
        when(jwtTokenProvider.generateAccessToken(any(), eq(userId), eq(tenantId)))
                .thenReturn("pin-access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("pin-refresh-token");
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(10000L);

        PinLoginRequest request = new PinLoginRequest();
        request.setPin("1234");

        AuthResponse response = authService.pinLogin(request);

        assertThat(response.getAccessToken()).isEqualTo("pin-access-token");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        verify(auditService, times(1)).logAuthEvent(eq(AuditAction.LOGIN_PIN), eq(userId), any());
    }

    @Test
    @DisplayName("multi mode: PIN login resolves the tenant from the subdomain")
    void shouldPinLoginByDomainInMultiMode() {
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "authMode", "multi");
        userEntity.setPin("hashed-pin");

        TenantEntity tenant = TenantEntity.builder().name("Shop A").isActive(true).build();
        tenant.setId(tenantId);

        when(tenantRepository.findByDomainOrSlug("shopa")).thenReturn(Optional.of(tenant));
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(tenantConfig));
        when(userRepository.findActiveUsersWithPinByTenantId(tenantId))
                .thenReturn(java.util.List.of(userEntity));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        when(pinLookupHasher.hash("1234")).thenReturn("lookup-1234");
        when(userDetailsService.loadUserByUsername("test@example.com"))
                .thenReturn(new User(userEntity.getEmail(), "dummy", Collections.emptyList()));
        when(jwtTokenProvider.generateAccessToken(any(), eq(userId), eq(tenantId)))
                .thenReturn("pin-access-token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("pin-refresh-token");
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(10000L);

        PinLoginRequest request = new PinLoginRequest();
        request.setPin("1234");

        AuthResponse response = authService.pinLogin(request, "shopa");

        assertThat(response.getAccessToken()).isEqualTo("pin-access-token");
        verify(tenantRepository, never()).findByIsActiveTrue();
    }

    @Test
    @DisplayName("multi mode: PIN login without a subdomain is rejected")
    void shouldRejectPinLoginWithoutDomainInMultiMode() {
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "authMode", "multi");

        PinLoginRequest request = new PinLoginRequest();
        request.setPin("1234");

        assertThatThrownBy(() -> authService.pinLogin(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("workspace not identified");
    }

    @Test
    @DisplayName("PIN login rejects a PIN shared by more than one account")
    void shouldRejectSharedPinLogin() {
        userEntity.setPin("hash-a");

        UserEntity other = UserEntity.builder().email("two@example.com")
                .firstName("Two").lastName("User").isActive(true)
                .roles(Collections.emptySet()).pin("hash-b").build();
        other.setId(UUID.randomUUID());
        other.setTenantId(tenantId);

        TenantEntity tenant = TenantEntity.builder().name("Acme").isActive(true).build();
        tenant.setId(tenantId);

        when(tenantRepository.findByIsActiveTrue()).thenReturn(java.util.List.of(tenant));
        when(tenantConfigurationRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(tenantConfig));
        when(userRepository.findActiveUsersWithPinByTenantId(tenantId))
                .thenReturn(java.util.List.of(userEntity, other));
        when(passwordEncoder.matches("1234", "hash-a")).thenReturn(true);
        when(passwordEncoder.matches("1234", "hash-b")).thenReturn(true);

        PinLoginRequest request = new PinLoginRequest();
        request.setPin("1234");

        assertThatThrownBy(() -> authService.pinLogin(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("more than one account");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Should revoke all tokens on logout")
    void shouldRevokeTokensOnLogout() {
        authService.logout(userId);

        verify(refreshTokenRepository, times(1)).revokeAllByUserId(userId);
        verify(auditService, times(1)).logAuthEvent(eq(AuditAction.LOGOUT), eq(userId));
    }

    @Nested
    @DisplayName("Refresh Token")
    class RefreshTokenTests {

        private RefreshTokenEntity buildToken(boolean revoked, LocalDateTime expiresAt) {
            return RefreshTokenEntity.builder()
                    .token("refresh-token")
                    .userId(userId)
                    .tenantId(tenantId)
                    .isRevoked(revoked)
                    .expiresAt(expiresAt)
                    .build();
        }

        @Test
        @DisplayName("Should reject a revoked refresh token")
        void shouldRejectRevokedToken() {
            RefreshTokenEntity revoked = buildToken(true, LocalDateTime.now().plusDays(1));

            when(refreshTokenRepository.findByTokenAndTenantId("refresh-token", tenantId))
                    .thenReturn(Optional.of(revoked));

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("refresh-token")
                    .tenantId(tenantId)
                    .build();

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("revoked");
        }

        @Test
        @DisplayName("Should reject an expired refresh token")
        void shouldRejectExpiredToken() {
            RefreshTokenEntity expired = buildToken(false, LocalDateTime.now().minusHours(1));

            when(refreshTokenRepository.findByTokenAndTenantId("refresh-token", tenantId))
                    .thenReturn(Optional.of(expired));

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("refresh-token")
                    .tenantId(tenantId)
                    .build();

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Should issue new tokens on valid refresh")
        void shouldIssueNewTokensOnValidRefresh() {
            RefreshTokenEntity valid = buildToken(false, LocalDateTime.now().plusDays(1));
            valid.setId(UUID.randomUUID());

            when(refreshTokenRepository.findByTokenAndTenantId("refresh-token", tenantId))
                    .thenReturn(Optional.of(valid));
            when(userRepository.findById(userId)).thenReturn(Optional.of(userEntity));
            when(tenantConfigurationRepository.findByTenantId(tenantId))
                    .thenReturn(Optional.of(tenantConfig));

            UserDetails userDetails = new User(userEntity.getEmail(), "dummy", Collections.emptyList());
            when(userDetailsService.loadUserByUsername(userEntity.getEmail())).thenReturn(userDetails);
            when(jwtTokenProvider.generateAccessToken(any(), eq(userId), eq(tenantId)))
                    .thenReturn("new-access-token");
            when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh-token");
            when(jwtProperties.getRefreshExpirationMs()).thenReturn(10000L);
            when(jwtProperties.getExpirationMs()).thenReturn(86400000L);

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("refresh-token")
                    .tenantId(tenantId)
                    .build();

            AuthResponse response = authService.refreshToken(request);

            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("Should reject token not found for tenant")
        void shouldRejectTokenNotFoundForTenant() {
            when(refreshTokenRepository.findByTokenAndTenantId("unknown-token", tenantId))
                    .thenReturn(Optional.empty());

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("unknown-token")
                    .tenantId(tenantId)
                    .build();

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid refresh token");
        }
    }
}
