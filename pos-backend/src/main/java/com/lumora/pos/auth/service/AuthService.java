package com.lumora.pos.auth.service;

import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.audit.service.AuditService;
import com.lumora.pos.auth.dto.AuthResponse;
import com.lumora.pos.auth.dto.LoginRequest;
import com.lumora.pos.auth.dto.MyProfileResponse;
import com.lumora.pos.auth.dto.PinLoginRequest;
import com.lumora.pos.auth.dto.RefreshTokenRequest;
import com.lumora.pos.auth.entity.RefreshTokenEntity;
import com.lumora.pos.auth.entity.RoleEntity;
import com.lumora.pos.auth.entity.UserEntity;
import com.lumora.pos.auth.repository.RefreshTokenRepository;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.config.JwtProperties;
import com.lumora.pos.config.JwtTokenProvider;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final AuditService auditService;
    private final com.lumora.pos.cashsession.service.CashSessionService cashSessionService;
    private final com.lumora.pos.superadmin.repository.TenantConfigurationRepository tenantConfigurationRepository;
    private final com.lumora.pos.superadmin.repository.TenantRepository tenantRepository;
    private final PinLookupHasher pinLookupHasher;

    /**
     * {@code single} (default): one business per database — the sole active tenant
     * is resolved automatically. {@code multi}: a shared database hosts many
     * tenants, so PIN login resolves the tenant from the {@code X-Tenant-Domain}
     * header (the shop's subdomain), since a 4-digit PIN carries no tenant info.
     */
    @org.springframework.beans.factory.annotation.Value("${app.auth.mode:single}")
    private String authMode;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Single business per deployment: the email alone identifies the user, so
        // we resolve the tenant from the user record rather than from the client.
        UserEntity user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElse(null);
        if (user == null) {
            auditService.logAuthEvent(AuditAction.LOGIN_FAILED, null,
                    Map.of("email", request.getEmail(), "method", "EMAIL_PASSWORD"));
            throw new BadCredentialsException("Invalid email or password");
        }

        TenantContext.setTenantId(user.getTenantId());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            // SaaS Governance: Ensure tenant is in a valid state
            validateTenantState(user.getTenantId());

            if (!user.isActive()) {
                throw new BusinessException("Your user account is deactivated.");
            }

            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            // Forced rotation: an admin-set/provisioned password is single-use.
            // Issue only a change-password-scoped token; no full session.
            if (user.isMustChangePassword()) {
                auditService.logAuthEvent(AuditAction.LOGIN, user.getId(),
                        Map.of("email", user.getEmail(), "method", "EMAIL_PASSWORD",
                                "passwordChangeRequired", true));
                return buildPasswordChangeResponse(user);
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
            AuthResponse response = createAuthResponse(user, userDetails);

            // Audit: Record successful email/password login
            auditService.logAuthEvent(AuditAction.LOGIN, user.getId(),
                    Map.of("email", user.getEmail(), "method", "EMAIL_PASSWORD"));

            return response;

        } catch (BadCredentialsException e) {
            // Audit: Record failed login attempt
            auditService.logAuthEvent(AuditAction.LOGIN_FAILED, null,
                    Map.of("email", request.getEmail(), "method", "EMAIL_PASSWORD"));
            throw new BadCredentialsException("Invalid email or password");
        } finally {
            TenantContext.clear();
        }
    }

    public AuthResponse pinLogin(PinLoginRequest request) {
        return pinLogin(request, null);
    }

    @Transactional
    public AuthResponse pinLogin(PinLoginRequest request, String tenantDomain) {
        // single mode: resolve the sole tenant; multi mode: resolve from the
        // shop's subdomain. Either way the cashier never types a tenant.
        UUID tenantId = resolveTenantForPinLogin(tenantDomain);
        TenantContext.setTenantId(tenantId);

        try {
            // SaaS Governance: Ensure tenant is in a valid state
            validateTenantState(tenantId);

            // Find user by tenant and PIN.
            // Iterate all users without early exit so response time is constant
            // regardless of which (if any) PIN matches — prevents timing oracle.
            // PINs are unique across the business (enforced when set), so at most
            // one user matches.
            List<UserEntity> users = userRepository.findActiveUsersWithPinByTenantId(tenantId);

            // Collect ALL matches (no early exit — also keeps timing constant).
            List<UserEntity> matches = new ArrayList<>();
            for (UserEntity user : users) {
                if (passwordEncoder.matches(request.getPin(), user.getPin())) {
                    matches.add(user);
                }
            }

            if (matches.isEmpty()) {
                // Audit: Record failed PIN login
                auditService.logAuthEvent(AuditAction.LOGIN_FAILED, null,
                        Map.of("method", "PIN", "tenantId", tenantId.toString()));
                throw new BadCredentialsException("Invalid PIN");
            }

            if (matches.size() > 1) {
                // A legacy duplicate PIN: refuse rather than silently sign in as
                // whichever account happened to be first. New PINs can't collide
                // (uniqueness is enforced when set); this guards pre-existing ones.
                auditService.logAuthEvent(AuditAction.LOGIN_FAILED, null,
                        Map.of("method", "PIN", "reason", "PIN_CONFLICT", "tenantId", tenantId.toString()));
                throw new BadCredentialsException(
                        "This PIN is used by more than one account. Please contact your administrator.");
            }

            UserEntity authenticatedUser = matches.get(0);

            // Lazily backfill the blind index for PINs predating V54, so the
            // admin PIN-conflict report can see them.
            if (authenticatedUser.getPinLookup() == null) {
                authenticatedUser.setPinLookup(pinLookupHasher.hash(request.getPin()));
            }

            authenticatedUser.setLastLoginAt(LocalDateTime.now());
            userRepository.save(authenticatedUser);

            // Forced rotation also applies to PIN login.
            if (authenticatedUser.isMustChangePassword()) {
                auditService.logAuthEvent(AuditAction.LOGIN_PIN, authenticatedUser.getId(),
                        Map.of("email", authenticatedUser.getEmail(), "method", "PIN",
                                "passwordChangeRequired", true));
                return buildPasswordChangeResponse(authenticatedUser);
            }

            // Load full details for JWT claims
            UserDetails userDetails = userDetailsService.loadUserByUsername(authenticatedUser.getEmail());

            AuthResponse response = createAuthResponse(authenticatedUser, userDetails);

            // Audit: Record successful PIN login
            auditService.logAuthEvent(AuditAction.LOGIN_PIN, authenticatedUser.getId(),
                    Map.of("email", authenticatedUser.getEmail(), "method", "PIN"));

            return response;

        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshTokenEntity refreshTokenEntity = refreshTokenRepository
                .findByTokenAndTenantId(request.getRefreshToken(), request.getTenantId())
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (refreshTokenEntity.isRevoked()) {
            throw new BusinessException("Refresh token has been revoked");
        }

        if (refreshTokenEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Refresh token has expired");
        }

        UserEntity user = userRepository.findById(refreshTokenEntity.getUserId())
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!user.isActive()) {
            throw new BusinessException("User account is deactivated");
        }

        // Set tenant context for user details loading
        TenantContext.setTenantId(user.getTenantId());
        try {
            // SaaS Governance: Ensure tenant is in a valid state
            validateTenantState(user.getTenantId());

            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

            // Issue new tokens
            String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails, user.getId(), user.getTenantId());

            // Optional: Rotate refresh token
            String newRefreshToken = jwtTokenProvider.generateRefreshToken();
            refreshTokenEntity.setToken(newRefreshToken);
            refreshTokenEntity
                    .setExpiresAt(LocalDateTime.now().plusNanos(jwtProperties.getRefreshExpirationMs() * 1_000_000L));
            refreshTokenRepository.save(refreshTokenEntity);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .expiresIn(jwtProperties.getExpirationMs())
                    .user(mapToDto(user, userDetails.getAuthorities()))
                    .build();
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);

        // Stale-session guard: close any cash drawer the user left open so it
        // doesn't suppress the Start Shift prompt on their next login. Best-effort
        // and isolated (REQUIRES_NEW) — never let it break the logout itself.
        try {
            cashSessionService.autoCloseOnLogout(userId);
        } catch (Exception e) {
            log.warn("Failed to auto-close cash session on logout for user {}", userId, e);
        }

        // Audit: Record logout
        auditService.logAuthEvent(AuditAction.LOGOUT, userId);
    }

    /** TTL for the forced-change-password token issued at login. */
    private static final long USER_PWCHANGE_TOKEN_TTL_MS = 15 * 60_000L;

    /**
     * Self-service password change. Used by both the forced-on-first-login flow
     * (caller holds a USER_PWCHANGE token) and ordinary profile-page rotations
     * (caller holds a full USER token). Clears the must-change flag and revokes
     * all refresh tokens so other sessions are invalidated.
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            auditService.logAuthEvent(AuditAction.LOGIN_FAILED, userId,
                    Map.of("reason", "WRONG_CURRENT_PASSWORD_ON_CHANGE"));
            throw new BadCredentialsException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("New password must differ from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(userId);
        auditService.logAuthEvent(AuditAction.PASSWORD_CHANGED, userId, null);
    }

    /** The authenticated user's own profile for the POS profile page. */
    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        return MyProfileResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .roles(user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toList()))
                .hasPin(user.getPin() != null && !user.getPin().isBlank())
                .primaryBranchId(user.getPrimaryBranch() != null ? user.getPrimaryBranch().getId() : null)
                .primaryBranchName(user.getPrimaryBranch() != null ? user.getPrimaryBranch().getName() : null)
                .branches(user.getBranches().stream()
                        .map(b -> MyProfileResponse.BranchRef.builder().id(b.getId()).name(b.getName()).build())
                        .collect(Collectors.toList()))
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    /**
     * Builds a response carrying only a short-lived, change-password-scoped token
     * (no refresh token) for a user whose password must be rotated.
     */
    private AuthResponse buildPasswordChangeResponse(UserEntity user) {
        String token = jwtTokenProvider.generateUserPasswordChangeToken(
                user.getId(), user.getTenantId(), USER_PWCHANGE_TOKEN_TTL_MS);

        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(null)
                .expiresIn(USER_PWCHANGE_TOKEN_TTL_MS)
                .passwordChangeRequired(true)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .tenantId(user.getTenantId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .build())
                .build();
    }

    /**
     * Chooses how PIN login finds its tenant, by deployment mode. In {@code multi}
     * (shared DB) the tenant comes from the shop's subdomain — supplied out-of-band
     * via {@code X-Tenant-Domain} — and only that tenant's PINs are ever searched,
     * so cross-tenant collisions remain impossible. In {@code single} it's the lone
     * active tenant.
     */
    private UUID resolveTenantForPinLogin(String tenantDomain) {
        if ("multi".equalsIgnoreCase(authMode)) {
            if (tenantDomain == null || tenantDomain.isBlank()) {
                throw new BusinessException("PIN login is unavailable: workspace not identified.");
            }
            return tenantRepository.findByDomainOrSlug(tenantDomain.trim())
                    .filter(com.lumora.pos.superadmin.entity.TenantEntity::isActive)
                    .map(com.lumora.pos.superadmin.entity.TenantEntity::getId)
                    .orElseThrow(() -> new BusinessException(
                            "PIN login is unavailable: workspace not found."));
        }
        return resolveSingleTenantId();
    }

    /**
     * Resolves the single active tenant for this deployment (one business per
     * server). PIN login has no email to key off, so it relies on there being
     * exactly one tenant. Zero or multiple is a deployment misconfiguration.
     */
    private UUID resolveSingleTenantId() {
        List<com.lumora.pos.superadmin.entity.TenantEntity> active = tenantRepository.findByIsActiveTrue();
        if (active.size() != 1) {
            throw new BusinessException("PIN login is unavailable: workspace is not configured.");
        }
        return active.get(0).getId();
    }

    private void validateTenantState(UUID tenantId) {
        com.lumora.pos.superadmin.entity.TenantConfigurationEntity config = tenantConfigurationRepository
                .findByTenantId(tenantId)
                .orElseGet(() -> {
                    log.info("Proactively provisioning missing configuration for tenant: {}", tenantId);
                    try {
                        com.lumora.pos.superadmin.entity.TenantConfigurationEntity newConfig =
                            com.lumora.pos.superadmin.entity.TenantConfigurationEntity.builder()
                                .tenantId(tenantId)
                                .planTier(com.lumora.pos.superadmin.enums.PlanTier.SMALL_BUSINESS)
                                .isActive(true)
                                .build();
                        return tenantConfigurationRepository.save(newConfig);
                    } catch (DataIntegrityViolationException e) {
                        // Concurrent first-login: another thread already inserted the config row.
                        return tenantConfigurationRepository.findByTenantId(tenantId)
                                .orElseThrow(() -> new BusinessException("Tenant configuration unavailable"));
                    }
                });

        if (!config.isActive()) {
            throw new BusinessException("Your tenant account has been suspended. Please contact your system administrator.");
        }

        if (config.isSubscriptionExpired()) {
            throw new BusinessException("Your subscription has expired. Please contact support to renew your plan.");
        }
    }

    private AuthResponse createAuthResponse(UserEntity user, UserDetails userDetails) {
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails, user.getId(), user.getTenantId());
        String refreshToken = jwtTokenProvider.generateRefreshToken();

        // Save refresh token to DB
        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .token(refreshToken)
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .expiresAt(LocalDateTime.now().plusNanos(jwtProperties.getRefreshExpirationMs() * 1_000_000L))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProperties.getExpirationMs())
                .user(mapToDto(user, userDetails.getAuthorities()))
                .build();
    }

    private AuthResponse.UserDto mapToDto(UserEntity user,
            Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
            
        com.lumora.pos.superadmin.entity.TenantConfigurationEntity config = tenantConfigurationRepository
                .findByTenantId(user.getTenantId())
                .orElse(null);

        List<String> features = config != null ? config.getFeaturesEnabled() : List.of();
        String planTier = config != null ? config.getPlanTier().name() : "UNKNOWN";

        return AuthResponse.UserDto.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toList()))
                .permissions(authorities.stream().map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .featuresEnabled(features)
                .planTier(planTier)
                .maxLocations(config != null ? config.getMaxLocations() : 1)
                .maxUsers(config != null ? config.getMaxUsers() : 5)
                .maxProducts(config != null ? config.getMaxProducts() : 500)
                .build();
    }
}
