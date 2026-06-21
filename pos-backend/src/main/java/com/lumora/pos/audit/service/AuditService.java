package com.lumora.pos.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.audit.entity.AuditLogEntity;
import com.lumora.pos.audit.repository.AuditLogRepository;
import com.lumora.pos.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Centralized Audit Logging Service for the Lumora POS System.
 *
 * <p>
 * Records all sensitive actions (financial transactions, CRUD operations,
 * authentication events) into the {@code audit_log} table for compliance
 * and operational traceability.
 * </p>
 *
 * <h3>Design Decisions:</h3>
 * <ul>
 * <li><b>Synchronous by default</b>: Audit writes participate in the same
 * transaction as the business operation. If the business operation rolls back,
 * the audit record rolls back too — preventing phantom audit entries.</li>
 * <li><b>REQUIRES_NEW for auth events</b>: Login/logout events run in their own
 * transaction since they don't participate in business transactions.</li>
 * <li><b>Fail-safe</b>: Audit failures are logged but never propagate
 * exceptions
 * to the caller. A failed audit write must NEVER break a sale.</li>
 * <li><b>RequestContextHolder</b>: Extracts IP and User-Agent from the current
 * HTTP request without requiring controller changes.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // PUBLIC API — Primary logging methods
    // =========================================================================

    /**
     * Log an action with full before/after entity snapshots.
     * Use for UPDATE and DELETE operations where you need the change trail.
     *
     * @param action     The audit action (e.g., AuditAction.UPDATE)
     * @param entityType The entity type string (e.g., "PRODUCT", "CATEGORY")
     * @param entityId   The ID of the affected entity
     * @param oldValue   The entity state BEFORE the change (will be serialized to
     *                   JSON)
     * @param newValue   The entity state AFTER the change (will be serialized to
     *                   JSON)
     */
    @Transactional
    public void log(AuditAction action, String entityType, UUID entityId,
            Object oldValue, Object newValue) {
        try {
            AuditLogEntity entry = buildBaseEntry(action, entityType, entityId);
            entry.setOldValue(serializeToJson(oldValue));
            entry.setNewValue(serializeToJson(newValue));
            auditLogRepository.save(entry);
            log.debug("Audit: {} {} [{}]", action, entityType, entityId);
        } catch (Exception e) {
            log.error("AUDIT WRITE FAILED — action={}, entityType={}, entityId={}",
                    action, entityType, entityId, e);
            // Fail-safe: Never let audit failure break the business operation.
        }
    }

    /**
     * Log a simple action without entity snapshots.
     * Use for CREATE and DELETE where only the action and entity reference matter,
     * or when capturing full snapshots is unnecessary.
     *
     * @param action     The audit action (e.g., AuditAction.CREATE)
     * @param entityType The entity type string (e.g., "BRAND")
     * @param entityId   The ID of the affected entity
     */
    @Transactional
    public void log(AuditAction action, String entityType, UUID entityId) {
        log(action, entityType, entityId, null, null);
    }

    /**
     * Log a CREATE action with the new entity snapshot.
     *
     * @param entityType The entity type string
     * @param entityId   The ID of the newly created entity
     * @param newValue   The new entity state
     */
    @Transactional
    public void logCreate(String entityType, UUID entityId, Object newValue) {
        log(AuditAction.CREATE, entityType, entityId, null, newValue);
    }

    /**
     * Log an UPDATE action with before/after snapshots.
     *
     * @param entityType The entity type string
     * @param entityId   The ID of the updated entity
     * @param oldValue   The entity state before the update
     * @param newValue   The entity state after the update
     */
    @Transactional
    public void logUpdate(String entityType, UUID entityId, Object oldValue, Object newValue) {
        log(AuditAction.UPDATE, entityType, entityId, oldValue, newValue);
    }

    /**
     * Log a DELETE action with the deleted entity snapshot.
     *
     * @param entityType The entity type string
     * @param entityId   The ID of the deleted entity
     * @param oldValue   The entity state before deletion
     */
    @Transactional
    public void logDelete(String entityType, UUID entityId, Object oldValue) {
        log(AuditAction.DELETE, entityType, entityId, oldValue, null);
    }

    /**
     * Log an authentication event (LOGIN, LOGOUT, LOGIN_FAILED).
     * Runs in its OWN transaction (REQUIRES_NEW) since auth events
     * do not participate in business transactions.
     *
     * @param action The auth action
     * @param userId The user who performed the action (nullable for failed logins)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthEvent(AuditAction action, UUID userId) {
        try {
            AuditLogEntity entry = AuditLogEntity.builder()
                    .tenantId(TenantContext.getTenantId())
                    .userId(userId)
                    .action(action.getValue())
                    .entityType("USER")
                    .entityId(userId)
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .build();

            // For auth events, tenant might not be set yet (e.g., login).
            // Use a safe fallback.
            if (entry.getTenantId() == null) {
                log.warn("Audit: TenantContext not set for auth event {}. Skipping.", action);
                return;
            }

            auditLogRepository.save(entry);
            log.debug("Audit: Auth event {} for user [{}]", action, userId);
        } catch (Exception e) {
            log.error("AUDIT WRITE FAILED — auth event={}, userId={}", action, userId, e);
        }
    }

    /**
     * Log an authentication event with additional detail in new_value
     * (e.g., the attempted email for a failed login).
     *
     * @param action  The auth action
     * @param userId  The user ID (nullable for failed logins)
     * @param details Additional details to record as JSONB
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthEvent(AuditAction action, UUID userId, Object details) {
        try {
            AuditLogEntity entry = AuditLogEntity.builder()
                    .tenantId(TenantContext.getTenantId())
                    .userId(userId)
                    .action(action.getValue())
                    .entityType("USER")
                    .entityId(userId)
                    .newValue(serializeToJson(details))
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .build();

            if (entry.getTenantId() == null) {
                log.warn("Audit: TenantContext not set for auth event {}. Skipping.", action);
                return;
            }

            auditLogRepository.save(entry);
            log.debug("Audit: Auth event {} for user [{}]", action, userId);
        } catch (Exception e) {
            log.error("AUDIT WRITE FAILED — auth event={}, userId={}", action, userId, e);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Builds a base audit log entry with all common fields populated.
     */
    private AuditLogEntity buildBaseEntry(AuditAction action, String entityType, UUID entityId) {
        return AuditLogEntity.builder()
                .tenantId(TenantContext.getTenantId())
                .userId(extractCurrentUserId())
                .action(action.getValue())
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(extractIpAddress())
                .userAgent(extractUserAgent())
                .build();
    }

    /**
     * Extracts the current authenticated user's ID from SecurityContext.
     * Returns null if no user is authenticated (e.g., system-triggered operations).
     */
    private UUID extractCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID) {
                return (UUID) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.trace("Could not extract user ID from SecurityContext", e);
        }
        return null;
    }

    /**
     * Extracts the client IP address from the current HTTP request.
     * Respects the X-Forwarded-For header for proxy environments.
     */
    private String extractIpAddress() {
        try {
            HttpServletRequest request = getCurrentHttpRequest();
            if (request == null)
                return null;

            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                // X-Forwarded-For may contain multiple IPs; the first is the client.
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.trace("Could not extract IP address from request", e);
            return null;
        }
    }

    /**
     * Extracts the User-Agent header from the current HTTP request.
     */
    private String extractUserAgent() {
        try {
            HttpServletRequest request = getCurrentHttpRequest();
            if (request == null)
                return null;

            String userAgent = request.getHeader("User-Agent");
            // Truncate to 500 chars to match DB column length
            return userAgent != null && userAgent.length() > 500
                    ? userAgent.substring(0, 500)
                    : userAgent;
        } catch (Exception e) {
            log.trace("Could not extract User-Agent from request", e);
            return null;
        }
    }

    /**
     * Gets the current HttpServletRequest from Spring's RequestContextHolder.
     * Returns null if called outside an HTTP request context (e.g., scheduled
     * tasks).
     */
    private HttpServletRequest getCurrentHttpRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Serializes an object to a JSON string for storage in the JSONB column.
     * Returns null for null input. Logs a warning on serialization failure.
     */
    private String serializeToJson(Object value) {
        if (value == null)
            return null;

        try {
            // Always use ObjectMapper — even for Strings — so the result is valid JSON.
            // e.g. "Approved by manager" → "\"Approved by manager\"" (valid JSONB)
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit value to JSON: {}", value.getClass().getSimpleName(), e);
            return "{\"error\": \"serialization_failed\", \"type\": \"" + value.getClass().getSimpleName() + "\"}";
        }
    }
}
