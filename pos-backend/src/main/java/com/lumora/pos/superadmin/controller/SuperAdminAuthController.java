package com.lumora.pos.superadmin.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.superadmin.dto.SuperAdminAuthResponse;
import com.lumora.pos.superadmin.dto.SuperAdminChangePasswordRequest;
import com.lumora.pos.superadmin.dto.SuperAdminLoginRequest;
import com.lumora.pos.superadmin.dto.SuperAdminProfileResponse;
import com.lumora.pos.superadmin.dto.SuperAdminRefreshRequest;
import com.lumora.pos.superadmin.service.SuperAdminAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

/**
 * REST controller for Super Admin authentication.
 *
 * Base path: /api/v1/super-admin/auth
 *
 * All endpoints below the {@code /auth/...} prefix are public at the
 * SecurityConfig level — Spring Security cannot validate credentials
 * before the user has any. Per-endpoint authorization (e.g., that the
 * change-password endpoint accepts only PWCHANGE tokens) is enforced
 * by the JwtAuthenticationFilter.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/super-admin/auth")
@RequiredArgsConstructor
public class SuperAdminAuthController {

    private final SuperAdminAuthService superAdminAuthService;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SuperAdminAuthResponse>> login(
            @Valid @RequestBody SuperAdminLoginRequest request,
            HttpServletResponse httpResponse) {

        log.info("Super admin login attempt for: {}", request.getEmail());
        SuperAdminAuthResponse response = superAdminAuthService.login(request);
        setSuperAdminCookie(httpResponse, response.getAccessToken());
        return ResponseEntity.ok(ApiResponse.success(response, "Super admin authenticated successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<SuperAdminAuthResponse>> refresh(
            @Valid @RequestBody SuperAdminRefreshRequest request,
            HttpServletResponse httpResponse) {
        SuperAdminAuthResponse response = superAdminAuthService.refresh(request.getRefreshToken());
        setSuperAdminCookie(httpResponse, response.getAccessToken());
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) SuperAdminRefreshRequest request,
            @AuthenticationPrincipal UUID superAdminId,
            HttpServletResponse httpResponse) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
        superAdminAuthService.logout(refreshToken, superAdminId);
        clearSuperAdminCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out"));
    }

    /**
     * Used both by the forced-on-first-login flow (with a PWCHANGE token)
     * and by self-service rotations (with a normal SUPERADMIN token).
     * The JwtAuthenticationFilter is responsible for letting only those
     * two token types reach this method.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody SuperAdminChangePasswordRequest request,
            @AuthenticationPrincipal UUID superAdminId) {
        if (superAdminId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        superAdminAuthService.changePassword(superAdminId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated"));
    }

    @GetMapping("/ping")
    public ResponseEntity<ApiResponse<String>> ping() {
        return ResponseEntity.ok(ApiResponse.success("Super admin session active"));
    }

    /**
     * Returns the authenticated super admin's profile plus last-login
     * surface for the account page. Rejected by the JwtAuthenticationFilter
     * if the caller holds a PWCHANGE-scoped token.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SuperAdminProfileResponse>> me(
            @AuthenticationPrincipal UUID superAdminId) {
        if (superAdminId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        return ResponseEntity.ok(ApiResponse.success(superAdminAuthService.getProfile(superAdminId)));
    }

    // -------------------------------------------------------------------------

    private void setSuperAdminCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("sa-auth-token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "Strict" : "Lax")
                .maxAge(Duration.ofDays(1))
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSuperAdminCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("sa-auth-token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "Strict" : "Lax")
                .maxAge(Duration.ZERO)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
