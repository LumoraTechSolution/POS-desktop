package com.lumora.pos.superadmin.service;

import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.config.JwtProperties;
import com.lumora.pos.config.JwtTokenProvider;
import com.lumora.pos.config.SuperAdminSecurityProperties;
import com.lumora.pos.superadmin.dto.SuperAdminAuthResponse;
import com.lumora.pos.superadmin.dto.SuperAdminLoginRequest;
import com.lumora.pos.superadmin.dto.SuperAdminProfileResponse;
import com.lumora.pos.superadmin.entity.SuperAdminEntity;
import com.lumora.pos.superadmin.entity.SuperAdminRefreshTokenEntity;
import com.lumora.pos.superadmin.repository.SuperAdminRefreshTokenRepository;
import com.lumora.pos.superadmin.repository.SuperAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication service for Super Admin login.
 *
 * Login flow (P1.1, P1.3, P1.4, P1.6):
 *   1. Look up super admin by email.
 *   2. If lockedUntil is in the future, reject (lockout).
 *   3. Verify account active.
 *   4. Verify password; on miss, increment counter and lock if threshold reached.
 *   5. On success: reset counter, update last_login_*, issue tokens.
 *   6. If passwordChangeRequired, the access token is scoped to /change-password.
 *   7. Every outcome is audited via SuperAdminAuditService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminAuthService {

    private final SuperAdminRepository                 superAdminRepository;
    private final SuperAdminRefreshTokenRepository     refreshTokenRepository;
    private final JwtTokenProvider                     jwtTokenProvider;
    private final JwtProperties                        jwtProperties;
    private final PasswordEncoder                      passwordEncoder;
    private final SuperAdminAuditService               auditService;
    private final SuperAdminSecurityProperties         securityProperties;

    @Transactional
    public SuperAdminAuthResponse login(SuperAdminLoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        SuperAdminEntity superAdmin = superAdminRepository.findByEmail(email).orElse(null);
        if (superAdmin == null) {
            auditService.logAuthEvent(AuditAction.SUPER_ADMIN_LOGIN_FAILED, null,
                    Map.of("attemptedEmail", email, "reason", "EMAIL_NOT_FOUND"));
            throw new BadCredentialsException("Invalid credentials");
        }

        // Lockout check first — even an active, correct account stays locked
        // until the cooldown elapses.
        if (superAdmin.getLockedUntil() != null && superAdmin.getLockedUntil().isAfter(LocalDateTime.now())) {
            auditService.logAuthEvent(AuditAction.SUPER_ADMIN_LOGIN_LOCKED, superAdmin.getId(),
                    Map.of("lockedUntil", superAdmin.getLockedUntil().toString()));
            throw new LockedException("Account temporarily locked. Try again later.");
        }

        if (!superAdmin.isActive()) {
            auditService.logAuthEvent(AuditAction.SUPER_ADMIN_LOGIN_FAILED, superAdmin.getId(),
                    Map.of("reason", "ACCOUNT_DEACTIVATED"));
            throw new BadCredentialsException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), superAdmin.getPasswordHash())) {
            registerFailedAttempt(superAdmin);
            throw new BadCredentialsException("Invalid credentials");
        }

        // Success path — reset counter, update last-login fields.
        superAdmin.setFailedLoginAttempts(0);
        superAdmin.setLockedUntil(null);
        superAdmin.setLastLoginAt(LocalDateTime.now());
        superAdmin.setLastLoginIp(currentRemoteIp());
        superAdmin.setLastLoginUserAgent(currentUserAgent());
        superAdminRepository.save(superAdmin);

        if (superAdmin.isPasswordChangeRequired()) {
            return buildPasswordChangeResponse(superAdmin);
        }

        SuperAdminAuthResponse response = buildFullSessionResponse(superAdmin);
        auditService.logAuthEvent(AuditAction.SUPER_ADMIN_LOGIN, superAdmin.getId(), null);
        return response;
    }

    @Transactional
    public SuperAdminAuthResponse refresh(String refreshTokenValue) {
        SuperAdminRefreshTokenEntity stored = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        SuperAdminEntity superAdmin = superAdminRepository.findById(stored.getSuperAdminId())
                .orElseThrow(() -> new BadCredentialsException("Super admin no longer exists"));

        if (!superAdmin.isActive()) {
            // Force the token off if account was deactivated mid-session.
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new BadCredentialsException("Account is deactivated");
        }

        // Rotation: revoke old, mint fresh.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return buildFullSessionResponse(superAdmin);
    }

    @Transactional
    public void logout(String refreshTokenValue, UUID superAdminId) {
        if (refreshTokenValue != null) {
            refreshTokenRepository.revokeByToken(refreshTokenValue);
        } else if (superAdminId != null) {
            // Defensive: clear all outstanding refresh tokens for this super admin
            // when the client didn't include the value (e.g., logout from a
            // different tab where sessionStorage was already cleared).
            refreshTokenRepository.revokeAllForSuperAdmin(superAdminId);
        }
        if (superAdminId != null) {
            auditService.logAuthEvent(AuditAction.SUPER_ADMIN_LOGOUT, superAdminId, null);
        }
    }

    @Transactional
    public void changePassword(UUID superAdminId, String currentPassword, String newPassword) {
        SuperAdminEntity superAdmin = superAdminRepository.findById(superAdminId)
                .orElseThrow(() -> new BadCredentialsException("Super admin not found"));

        if (!passwordEncoder.matches(currentPassword, superAdmin.getPasswordHash())) {
            auditService.logAuthEvent(AuditAction.SUPER_ADMIN_LOGIN_FAILED, superAdminId,
                    Map.of("reason", "WRONG_CURRENT_PASSWORD_ON_CHANGE"));
            throw new BadCredentialsException("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, superAdmin.getPasswordHash())) {
            throw new BadCredentialsException("New password must differ from current password");
        }

        superAdmin.setPasswordHash(passwordEncoder.encode(newPassword));
        superAdmin.setPasswordChangeRequired(false);
        superAdminRepository.save(superAdmin);

        // Revoke every outstanding refresh token so other sessions are invalidated.
        refreshTokenRepository.revokeAllForSuperAdmin(superAdminId);

        auditService.logAuthEvent(AuditAction.SUPER_ADMIN_PASSWORD_CHANGED, superAdminId, null);
    }

    public boolean isValidSuperAdmin(String email) {
        return superAdminRepository.findByEmailAndIsActive(email, true).isPresent();
    }

    @Transactional(readOnly = true)
    public SuperAdminProfileResponse getProfile(UUID superAdminId) {
        SuperAdminEntity sa = superAdminRepository.findById(superAdminId)
                .orElseThrow(() -> new BadCredentialsException("Super admin not found"));

        return SuperAdminProfileResponse.builder()
                .id(sa.getId())
                .email(sa.getEmail())
                .firstName(sa.getFirstName())
                .lastName(sa.getLastName())
                .fullName(sa.getFullName())
                .role("SUPERADMIN")
                .lastLoginAt(sa.getLastLoginAt())
                .lastLoginIp(sa.getLastLoginIp())
                .lastLoginUserAgent(sa.getLastLoginUserAgent())
                .createdAt(sa.getCreatedAt())
                .build();
    }

    // -------------------------------------------------------------------------
    // INTERNALS
    // -------------------------------------------------------------------------

    private void registerFailedAttempt(SuperAdminEntity superAdmin) {
        int attempts = superAdmin.getFailedLoginAttempts() + 1;
        superAdmin.setFailedLoginAttempts(attempts);
        boolean nowLocked = false;
        if (attempts >= securityProperties.getMaxFailedAttempts()) {
            superAdmin.setLockedUntil(LocalDateTime.now()
                    .plusMinutes(securityProperties.getLockoutDurationMinutes()));
            superAdmin.setFailedLoginAttempts(0);
            nowLocked = true;
        }
        superAdminRepository.save(superAdmin);

        auditService.logAuthEvent(
                nowLocked ? AuditAction.SUPER_ADMIN_LOGIN_LOCKED : AuditAction.SUPER_ADMIN_LOGIN_FAILED,
                superAdmin.getId(),
                Map.of("attempts", attempts,
                        "lockedUntil", nowLocked ? superAdmin.getLockedUntil().toString() : "")
        );
    }

    private SuperAdminAuthResponse buildFullSessionResponse(SuperAdminEntity superAdmin) {
        String accessToken = jwtTokenProvider.generateSuperAdminToken(superAdmin.getId(), superAdmin.getEmail());

        SuperAdminRefreshTokenEntity refresh = SuperAdminRefreshTokenEntity.builder()
                .token(jwtTokenProvider.generateRefreshToken())
                .superAdminId(superAdmin.getId())
                .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(jwtProperties.getRefreshExpirationMs())))
                .build();
        refreshTokenRepository.save(refresh);

        return SuperAdminAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refresh.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs())
                .passwordChangeRequired(false)
                .superAdmin(SuperAdminAuthResponse.SuperAdminDto.builder()
                        .id(superAdmin.getId())
                        .email(superAdmin.getEmail())
                        .firstName(superAdmin.getFirstName())
                        .lastName(superAdmin.getLastName())
                        .fullName(superAdmin.getFullName())
                        .role("SUPERADMIN")
                        .passwordChangeRequired(false)
                        .build())
                .build();
    }

    private SuperAdminAuthResponse buildPasswordChangeResponse(SuperAdminEntity superAdmin) {
        long ttlMs = securityProperties.getPasswordChangeTokenTtlMinutes() * 60_000L;
        String pwToken = jwtTokenProvider.generateSuperAdminPasswordChangeToken(
                superAdmin.getId(), superAdmin.getEmail(), ttlMs);

        return SuperAdminAuthResponse.builder()
                .accessToken(pwToken)
                .refreshToken(null)
                .tokenType("Bearer")
                .expiresIn(ttlMs)
                .passwordChangeRequired(true)
                .superAdmin(SuperAdminAuthResponse.SuperAdminDto.builder()
                        .id(superAdmin.getId())
                        .email(superAdmin.getEmail())
                        .firstName(superAdmin.getFirstName())
                        .lastName(superAdmin.getLastName())
                        .fullName(superAdmin.getFullName())
                        .role("SUPERADMIN")
                        .passwordChangeRequired(true)
                        .build())
                .build();
    }

    private String currentRemoteIp() {
        try {
            jakarta.servlet.http.HttpServletRequest req = currentRequest();
            if (req == null) return null;
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String currentUserAgent() {
        try {
            jakarta.servlet.http.HttpServletRequest req = currentRequest();
            if (req == null) return null;
            String ua = req.getHeader("User-Agent");
            return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
        } catch (Exception e) {
            return null;
        }
    }

    private jakarta.servlet.http.HttpServletRequest currentRequest() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
