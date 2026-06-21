package com.lumora.pos.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT token generation, validation, and claim extraction.
 * Uses JJWT 0.12.x API with HS256 signing.
 *
 * Supports two token types:
 *   1. Tenant user tokens  — include tenantId claim, tokenType = "USER"
 *   2. Super admin tokens  — tenantId claim is absent, tokenType = "SUPERADMIN"
 *
 * The JwtAuthenticationFilter detects the token type via the "tokenType" claim
 * and routes authentication accordingly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private static final String CLAIM_TENANT_ID   = "tenantId";
    private static final String CLAIM_AUTHORITIES = "authorities";
    private static final String CLAIM_TOKEN_TYPE  = "tokenType";

    public static final String TOKEN_TYPE_USER                = "USER";
    public static final String TOKEN_TYPE_SUPERADMIN          = "SUPERADMIN";
    /** Short-lived token issued when password_change_required=true. Only the
     *  POST /super-admin/auth/change-password endpoint accepts it. */
    public static final String TOKEN_TYPE_SUPERADMIN_PWCHANGE = "SUPERADMIN_PWCHANGE";
    /** Tenant-user equivalent of the above. Issued when a user's
     *  must_change_password=true; only POST /api/v1/auth/change-password accepts it. */
    public static final String TOKEN_TYPE_USER_PWCHANGE       = "USER_PWCHANGE";

    // -----------------------------------------------------------------------
    // Token Generation — Tenant Users
    // -----------------------------------------------------------------------

    /**
     * Generate a signed access token for a regular tenant user.
     * Includes tenantId claim and tokenType = "USER".
     *
     * @param userDetails Spring Security user details
     * @param userId      UUID of the authenticated user
     * @param tenantId    UUID of the tenant the user belongs to
     * @return signed JWT string
     */
    public String generateAccessToken(UserDetails userDetails, UUID userId, UUID tenantId) {
        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TENANT_ID,   tenantId.toString())
                .claim(CLAIM_AUTHORITIES, authorities)
                .claim(CLAIM_TOKEN_TYPE,  TOKEN_TYPE_USER)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    // -----------------------------------------------------------------------
    // Token Generation — Super Admins
    // -----------------------------------------------------------------------

    /**
     * Generate a signed access token for a Super Admin.
     * Does NOT include a tenantId claim. tokenType = "SUPERADMIN".
     * The filter will detect this and skip tenant context setup.
     *
     * @param superAdminId UUID of the super admin
     * @param email        Super admin email (used as subject alternative)
     * @return signed JWT string
     */
    public String generateSuperAdminToken(UUID superAdminId, String email) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(superAdminId.toString())
                .claim(CLAIM_AUTHORITIES, List.of("ROLE_SUPERADMIN"))
                .claim(CLAIM_TOKEN_TYPE,  TOKEN_TYPE_SUPERADMIN)
                .claim("email",           email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generate an opaque UUID refresh token (stored in DB, not self-contained).
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Short-lived token usable only against POST /super-admin/auth/change-password.
     * Issued at login when {@code password_change_required = true}; the
     * JwtAuthenticationFilter rejects it on every other route.
     */
    public String generateSuperAdminPasswordChangeToken(UUID superAdminId, String email, long ttlMs) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);

        return Jwts.builder()
                .subject(superAdminId.toString())
                .claim(CLAIM_AUTHORITIES, List.of("ROLE_SUPERADMIN_PWCHANGE"))
                .claim(CLAIM_TOKEN_TYPE,  TOKEN_TYPE_SUPERADMIN_PWCHANGE)
                .claim("email",           email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Tenant-user "must change password" token. Carries the tenantId claim so
     * TenantContext still resolves (the change-password call needs it), but the
     * filter restricts it to POST /api/v1/auth/change-password only.
     */
    public String generateUserPasswordChangeToken(UUID userId, UUID tenantId, long ttlMs) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TENANT_ID,   tenantId.toString())
                .claim(CLAIM_AUTHORITIES, List.of("ROLE_USER_PWCHANGE"))
                .claim(CLAIM_TOKEN_TYPE,  TOKEN_TYPE_USER_PWCHANGE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /** True when the token is the super-admin short-lived "must change password" token. */
    public boolean isPasswordChangeToken(String token) {
        return TOKEN_TYPE_SUPERADMIN_PWCHANGE.equals(getTokenType(token));
    }

    /** True when the token is the tenant-user short-lived "must change password" token. */
    public boolean isUserPasswordChangeToken(String token) {
        return TOKEN_TYPE_USER_PWCHANGE.equals(getTokenType(token));
    }

    // -----------------------------------------------------------------------
    // Token Validation
    // -----------------------------------------------------------------------

    /**
     * Validate the token signature and expiry.
     *
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Claim Extraction
    // -----------------------------------------------------------------------

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * Extracts the tenantId claim. Returns null for super admin tokens
     * (which intentionally omit this claim).
     */
    public UUID getTenantIdFromToken(String token) {
        String tenantIdStr = parseClaims(token).get(CLAIM_TENANT_ID, String.class);
        return tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
    }

    /**
     * Returns the token type: "USER" for tenant users, "SUPERADMIN" for operators.
     * Returns "USER" as default if claim is missing (backward compatibility).
     */
    public String getTokenType(String token) {
        String tokenType = parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
        return tokenType != null ? tokenType : TOKEN_TYPE_USER;
    }

    /** Returns true if this token belongs to a Super Admin. */
    public boolean isSuperAdminToken(String token) {
        return TOKEN_TYPE_SUPERADMIN.equals(getTokenType(token));
    }

    @SuppressWarnings("unchecked")
    public List<String> getAuthoritiesFromToken(String token) {
        return parseClaims(token).get(CLAIM_AUTHORITIES, List.class);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
