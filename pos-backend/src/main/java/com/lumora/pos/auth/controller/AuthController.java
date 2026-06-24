package com.lumora.pos.auth.controller;

import com.lumora.pos.auth.dto.AuthResponse;
import com.lumora.pos.auth.dto.ChangePasswordRequest;
import com.lumora.pos.auth.dto.LoginRequest;
import com.lumora.pos.auth.dto.MyProfileResponse;
import com.lumora.pos.auth.dto.PinLoginRequest;
import com.lumora.pos.auth.dto.RefreshTokenRequest;
import com.lumora.pos.auth.service.AuthService;
import com.lumora.pos.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse auth = authService.login(request);
        setAuthCookie(response, auth.getAccessToken());
        return ResponseEntity.ok(ApiResponse.success(auth, "Login successful"));
    }

    @PostMapping("/pin-login")
    public ResponseEntity<ApiResponse<AuthResponse>> pinLogin(
            @Valid @RequestBody PinLoginRequest request,
            @RequestHeader(value = "X-Tenant-Domain", required = false) String tenantDomain,
            HttpServletResponse response) {
        // X-Tenant-Domain (the shop's subdomain) is only consulted in multi-tenant
        // mode; in single-tenant deployments it's ignored.
        AuthResponse auth = authService.pinLogin(request, tenantDomain);
        setAuthCookie(response, auth.getAccessToken());
        return ResponseEntity.ok(ApiResponse.success(auth, "PIN Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletResponse response) {
        AuthResponse auth = authService.refreshToken(request);
        setAuthCookie(response, auth.getAccessToken());
        return ResponseEntity.ok(ApiResponse.success(auth, "Token refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UUID userId, HttpServletResponse response) {
        authService.logout(userId);
        clearAuthCookie(response);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Logged out successfully")
                .build());
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("auth-token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "Strict" : "Lax")
                .maxAge(Duration.ofDays(1)) // keep in sync with app.jwt.refresh-expiration-ms
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("auth-token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "Strict" : "Lax")
                .maxAge(Duration.ZERO)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Self-service password change. Accepts both the forced-on-first-login
     * USER_PWCHANGE token (restricted to this path by JwtAuthenticationFilter)
     * and a normal USER token from the profile page. The /api/v1/auth/** path is
     * public at the SecurityConfig level, so we enforce authentication here.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UUID userId,
            HttpServletResponse response) {
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        // The scoped token is now spent; clear the cookie so the client must re-login.
        clearAuthCookie(response);
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> me(@AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                authService.getMyProfile(userId), "Current user fetched"));
    }
}
