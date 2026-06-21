package com.lumora.pos.superadmin.service;

import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.config.JwtProperties;
import com.lumora.pos.config.JwtTokenProvider;
import com.lumora.pos.config.SuperAdminSecurityProperties;
import com.lumora.pos.superadmin.dto.SuperAdminAuthResponse;
import com.lumora.pos.superadmin.dto.SuperAdminLoginRequest;
import com.lumora.pos.superadmin.entity.SuperAdminEntity;
import com.lumora.pos.superadmin.repository.SuperAdminRefreshTokenRepository;
import com.lumora.pos.superadmin.repository.SuperAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminAuthService Unit Tests")
class SuperAdminAuthServiceTest {

    @Mock private SuperAdminRepository superAdminRepository;
    @Mock private SuperAdminRefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtProperties jwtProperties;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SuperAdminAuditService auditService;
    @Mock private SuperAdminSecurityProperties securityProperties;

    @InjectMocks private SuperAdminAuthService authService;

    private UUID superAdminId;
    private SuperAdminEntity admin;

    @BeforeEach
    void setUp() {
        superAdminId = UUID.randomUUID();
        admin = SuperAdminEntity.builder()
                .id(superAdminId)
                .email("ops@lumora.com")
                .passwordHash("hash")
                .firstName("Ops")
                .lastName("Lumora")
                .isActive(true)
                .failedLoginAttempts(0)
                .passwordChangeRequired(false)
                .build();
    }

    private SuperAdminLoginRequest req(String email, String password) {
        SuperAdminLoginRequest r = new SuperAdminLoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    @Test
    @DisplayName("login succeeds, audits SUPER_ADMIN_LOGIN, returns full session")
    void login_success_returnsTokensAndAudits() {
        when(superAdminRepository.findByEmail("ops@lumora.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        when(jwtTokenProvider.generateSuperAdminToken(superAdminId, "ops@lumora.com")).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProperties.getExpirationMs()).thenReturn(86_400_000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604_800_000L);

        SuperAdminAuthResponse response = authService.login(req("ops@lumora.com", "correct"));

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.isPasswordChangeRequired()).isFalse();
        verify(auditService).logAuthEvent(eq(AuditAction.SUPER_ADMIN_LOGIN), eq(superAdminId), any());
        verify(superAdminRepository).save(admin);
        assertThat(admin.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("login with unknown email audits failure and rejects")
    void login_unknownEmail_audited() {
        when(superAdminRepository.findByEmail("ghost@lumora.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req("ghost@lumora.com", "x")))
                .isInstanceOf(BadCredentialsException.class);

        verify(auditService).logAuthEvent(eq(AuditAction.SUPER_ADMIN_LOGIN_FAILED), eq(null), any());
        verify(superAdminRepository, never()).save(any());
    }

    @Test
    @DisplayName("login on deactivated account audits and rejects")
    void login_deactivated_rejected() {
        admin.setActive(false);
        when(superAdminRepository.findByEmail("ops@lumora.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.login(req("ops@lumora.com", "x")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("deactivated");

        verify(auditService).logAuthEvent(eq(AuditAction.SUPER_ADMIN_LOGIN_FAILED), eq(superAdminId), any());
    }

    @Test
    @DisplayName("locked account rejects with LockedException even with correct password")
    void login_locked_rejected() {
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(superAdminRepository.findByEmail("ops@lumora.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.login(req("ops@lumora.com", "anything")))
                .isInstanceOf(LockedException.class);

        verify(auditService).logAuthEvent(eq(AuditAction.SUPER_ADMIN_LOGIN_LOCKED), eq(superAdminId), any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Nth failed attempt sets lockedUntil and audits SUPER_ADMIN_LOGIN_LOCKED")
    void login_thresholdReached_locksAccount() {
        admin.setFailedLoginAttempts(4); // one shy of the 5 threshold
        when(superAdminRepository.findByEmail("ops@lumora.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(securityProperties.getMaxFailedAttempts()).thenReturn(5);
        when(securityProperties.getLockoutDurationMinutes()).thenReturn(15);

        assertThatThrownBy(() -> authService.login(req("ops@lumora.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(admin.getLockedUntil()).isAfter(LocalDateTime.now());
        // After lock the counter resets to 0 so a subsequent unlock starts clean.
        assertThat(admin.getFailedLoginAttempts()).isZero();
        ArgumentCaptor<AuditAction> action = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditService).logAuthEvent(action.capture(), eq(superAdminId), any());
        assertThat(action.getValue()).isEqualTo(AuditAction.SUPER_ADMIN_LOGIN_LOCKED);
    }

    @Test
    @DisplayName("login returns short-lived PWCHANGE response when passwordChangeRequired=true")
    void login_passwordChangeRequired_returnsPwChangeResponse() {
        admin.setPasswordChangeRequired(true);
        when(superAdminRepository.findByEmail("ops@lumora.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        when(securityProperties.getPasswordChangeTokenTtlMinutes()).thenReturn(5);
        when(jwtTokenProvider.generateSuperAdminPasswordChangeToken(eq(superAdminId), eq("ops@lumora.com"), eq(5L * 60_000L)))
                .thenReturn("pw-change-token");

        SuperAdminAuthResponse response = authService.login(req("ops@lumora.com", "correct"));

        assertThat(response.isPasswordChangeRequired()).isTrue();
        assertThat(response.getAccessToken()).isEqualTo("pw-change-token");
        assertThat(response.getRefreshToken()).isNull();
        // No SUPER_ADMIN_LOGIN audit row on the pw-change path — only the
        // successful full-session path records that action.
        verify(auditService, never()).logAuthEvent(eq(AuditAction.SUPER_ADMIN_LOGIN), any(), any());
    }

    @Test
    @DisplayName("changePassword rotates hash, clears flag, revokes refresh tokens, audits")
    void changePassword_happyPath() {
        when(superAdminRepository.findById(superAdminId)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.matches("new", "hash")).thenReturn(false);
        when(passwordEncoder.encode("new")).thenReturn("new-hash");

        authService.changePassword(superAdminId, "old", "new");

        assertThat(admin.getPasswordHash()).isEqualTo("new-hash");
        assertThat(admin.isPasswordChangeRequired()).isFalse();
        verify(refreshTokenRepository).revokeAllForSuperAdmin(superAdminId);
        verify(auditService).logAuthEvent(eq(AuditAction.SUPER_ADMIN_PASSWORD_CHANGED), eq(superAdminId), any());
    }

    @Test
    @DisplayName("changePassword rejects wrong current password and audits")
    void changePassword_wrongCurrent_audited() {
        when(superAdminRepository.findById(superAdminId)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(superAdminId, "wrong", "new"))
                .isInstanceOf(BadCredentialsException.class);

        verify(auditService, times(1)).logAuthEvent(eq(AuditAction.SUPER_ADMIN_LOGIN_FAILED), eq(superAdminId), any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("changePassword rejects reusing the same password")
    void changePassword_sameAsCurrent_rejected() {
        when(superAdminRepository.findById(superAdminId)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("samepw", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(superAdminId, "samepw", "samepw"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("differ");
    }
}
