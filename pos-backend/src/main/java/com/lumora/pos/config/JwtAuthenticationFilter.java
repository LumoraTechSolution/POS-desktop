package com.lumora.pos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.pos.auth.service.CustomUserDetailsService;
import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.superadmin.repository.TenantConfigurationRepository;
import com.lumora.pos.superadmin.service.SuperAdminUserDetailsService;
import com.lumora.pos.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Intercepts every request, extracts the Bearer JWT, validates it,
 * and populates the SecurityContext + TenantContext.
 *
 * Token routing:
 *   - SUPERADMIN_PWCHANGE → only the change-password endpoint accepts it.
 *   - SUPERADMIN          → full super admin access; no TenantContext.
 *   - USER                → tenant user; sets TenantContext, rejects
 *                           with 401 if the tenant is currently suspended.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/api/v1/super-admin/auth/change-password";
    private static final String USER_CHANGE_PASSWORD_PATH = "/api/v1/auth/change-password";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final SuperAdminUserDetailsService superAdminUserDetailsService;
    private final TenantConfigurationRepository tenantConfigurationRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                if (jwtTokenProvider.isPasswordChangeToken(token)) {
                    if (!CHANGE_PASSWORD_PATH.equals(request.getRequestURI())) {
                        writeUnauthorized(response, "Password change required before this action");
                        return;
                    }
                    handlePasswordChangeToken(token, request);
                } else if (jwtTokenProvider.isUserPasswordChangeToken(token)) {
                    if (!USER_CHANGE_PASSWORD_PATH.equals(request.getRequestURI())) {
                        writeUnauthorized(response, "Password change required before this action");
                        return;
                    }
                    if (!handleUserPasswordChangeToken(token, request, response)) {
                        return; // response already written (suspended tenant)
                    }
                } else if (jwtTokenProvider.isSuperAdminToken(token)) {
                    handleSuperAdminToken(token, request);
                } else {
                    boolean accepted = handleTenantUserToken(token, request, response);
                    if (!accepted) {
                        return; // response already written
                    }
                }
            } catch (Exception e) {
                log.error("Could not set user authentication from JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
                TenantContext.clear();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Token Handlers
    // -----------------------------------------------------------------------

    private void handlePasswordChangeToken(String token, HttpServletRequest request) {
        UUID superAdminId = jwtTokenProvider.getUserIdFromToken(token);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        superAdminId,
                        null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_SUPERADMIN_PWCHANGE")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Tenant-user forced-password-change token. Sets TenantContext so the
     * change-password service can resolve the user within its tenant, and grants
     * only ROLE_USER_PWCHANGE. Honours tenant suspension like a normal user token.
     *
     * @return true to continue, false if the response was already written.
     */
    private boolean handleUserPasswordChangeToken(String token, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        UUID userId   = jwtTokenProvider.getUserIdFromToken(token);
        UUID tenantId = jwtTokenProvider.getTenantIdFromToken(token);

        if (tenantId == null) {
            log.warn("User pwchange token missing tenantId claim — rejecting.");
            return true; // let downstream entry point return 401
        }

        boolean tenantActive = tenantConfigurationRepository.findByTenantId(tenantId)
                .map(c -> c.isActive())
                .orElse(true);
        if (!tenantActive) {
            writeUnauthorized(response, "Tenant suspended. Contact your administrator.");
            return false;
        }

        TenantContext.setTenantId(tenantId);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_USER_PWCHANGE")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return true;
    }

    private void handleSuperAdminToken(String token, HttpServletRequest request) {
        UUID superAdminId = jwtTokenProvider.getUserIdFromToken(token);

        UserDetails superAdminDetails = superAdminUserDetailsService.loadUserById(superAdminId);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        superAdminId,
                        null,
                        superAdminDetails.getAuthorities());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Super admin authenticated: {}", superAdminId);
    }

    /**
     * @return true if authentication should continue, false if the
     *         response has already been written (suspended tenant).
     */
    private boolean handleTenantUserToken(String token, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        UUID userId   = jwtTokenProvider.getUserIdFromToken(token);
        UUID tenantId = jwtTokenProvider.getTenantIdFromToken(token);

        if (tenantId == null) {
            log.warn("Tenant user token missing tenantId claim — rejecting.");
            return true; // let downstream entry point return 401
        }

        // Tenant suspension takes effect immediately. The repository is
        // @Cacheable("tenantConfigs"); SuperAdminTenantService.suspendTenant
        // / activateTenant evict the same cache, so the new state is
        // visible within milliseconds.
        boolean tenantActive = tenantConfigurationRepository.findByTenantId(tenantId)
                .map(c -> c.isActive())
                .orElse(true); // missing config: don't lock the user out
        if (!tenantActive) {
            log.warn("Rejecting request: tenant {} is suspended", tenantId);
            writeUnauthorized(response, "Tenant suspended. Contact your administrator.");
            return false;
        }

        TenantContext.setTenantId(tenantId);

        UserDetails userDetails = userDetailsService.loadUserById(userId);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        userDetails.getAuthorities());

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Object> body = ApiResponse.error(message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // Cookie fallback (used when the SPA's in-memory access token is gone, e.g.
        // after a page refresh). Scope the cookie to the request namespace so a
        // super-admin session cookie can never authenticate tenant endpoints — and
        // vice versa — when both cookies are present in the same browser. Otherwise
        // a stale `sa-auth-token` would authenticate tenant calls as ROLE_SUPERADMIN,
        // which lacks tenant roles and 403s the POS terminal.
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String wanted = request.getRequestURI().startsWith("/api/v1/super-admin")
                    ? "sa-auth-token" : "auth-token";
            for (Cookie c : cookies) {
                if (wanted.equals(c.getName()) && StringUtils.hasText(c.getValue())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}
