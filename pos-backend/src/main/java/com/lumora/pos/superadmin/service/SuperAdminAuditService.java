package com.lumora.pos.superadmin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.pos.audit.AuditAction;
import com.lumora.pos.audit.entity.AuditLogEntity;
import com.lumora.pos.audit.repository.AuditLogRepository;
import com.lumora.pos.superadmin.dto.SuperAdminAuditResponse;
import com.lumora.pos.superadmin.entity.SuperAdminAuditLogEntity;
import com.lumora.pos.superadmin.entity.TenantEntity;
import com.lumora.pos.superadmin.repository.SuperAdminAuditLogRepository;
import com.lumora.pos.superadmin.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminAuditService {

    private final AuditLogRepository auditLogRepository;
    private final SuperAdminAuditLogRepository superAdminAuditLogRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // READS — Cross-tenant view of the regular audit_log
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<SuperAdminAuditResponse> getGlobalAuditLogs(
            String search,
            String action,
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            Pageable pageable) {
        String cleanSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : "";
        String cleanAction = (action != null && !action.trim().isEmpty()) ? action.trim() : "";
        // Open-ended date bounds use sentinels, never NULL: a NULL bound has no type
        // anchor in the query's WHERE clause and PostgreSQL rejects it with
        // "could not determine data type of parameter". See searchGlobalFiltered.
        java.time.LocalDateTime effectiveStart = (startDate != null)
                ? startDate : java.time.LocalDateTime.of(1970, 1, 1, 0, 0);
        java.time.LocalDateTime effectiveEnd = (endDate != null)
                ? endDate : java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59);

        Page<AuditLogEntity> auditPage = auditLogRepository.searchGlobalFiltered(
                cleanSearch, cleanAction, effectiveStart, effectiveEnd, pageable);

        var tenantIds = auditPage.getContent().stream()
                .map(AuditLogEntity::getTenantId)
                .distinct()
                .toList();

        Map<java.util.UUID, String> tenantNameMap = tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(TenantEntity::getId, TenantEntity::getName));

        return auditPage.map(log -> SuperAdminAuditResponse.builder()
                .id(log.getId())
                .tenantId(log.getTenantId())
                .tenantName(tenantNameMap.getOrDefault(log.getTenantId(), "Unknown Tenant"))
                .userId(log.getUserId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build()
        );
    }

    /**
     * Distinct action codes present in audit_log. Drives the audit-log
     * action multi-select.
     */
    @Transactional(readOnly = true)
    public List<String> getDistinctActions() {
        return auditLogRepository.findDistinctActions();
    }

    /**
     * Streams a CSV export of the same filtered audit query. Uses a hard
     * row cap to keep a curl-happy admin from accidentally dumping the
     * whole table in one request.
     */
    @Transactional(readOnly = true)
    public void streamAuditLogCsv(
            String search,
            String action,
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            OutputStream out) throws IOException {
        final int MAX_ROWS = 50_000;
        Pageable pageable = PageRequest.of(0, MAX_ROWS, Sort.by("createdAt").descending());

        Page<SuperAdminAuditResponse> page = getGlobalAuditLogs(search, action, startDate, endDate, pageable);

        DateTimeFormatter ts = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.println("created_at,tenant_id,tenant_name,user_id,action,entity_type,entity_id,ip_address,old_value,new_value");
            for (SuperAdminAuditResponse row : page.getContent()) {
                writer.print(row.getCreatedAt() != null ? row.getCreatedAt().format(ts) : "");
                writer.print(',');
                writer.print(csv(row.getTenantId()));
                writer.print(',');
                writer.print(csv(row.getTenantName()));
                writer.print(',');
                writer.print(csv(row.getUserId()));
                writer.print(',');
                writer.print(csv(row.getAction()));
                writer.print(',');
                writer.print(csv(row.getEntityType()));
                writer.print(',');
                writer.print(csv(row.getEntityId()));
                writer.print(',');
                writer.print(csv(row.getIpAddress()));
                writer.print(',');
                writer.print(csv(row.getOldValue()));
                writer.print(',');
                writer.println(csv(row.getNewValue()));
            }
            writer.flush();
        }
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String s = value.toString();
        // Escape per RFC 4180: wrap when the field contains a comma, quote,
        // newline, or carriage return; double up any embedded quotes.
        boolean mustQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!mustQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // =========================================================================
    // WRITES — Super-admin events to super_admin_audit_log
    // =========================================================================

    /**
     * Authentication event: login success/failure/lockout/logout.
     * REQUIRES_NEW so the audit row commits even if the surrounding
     * auth flow rolls back (e.g., wrong password throws after counter
     * increment).
     *
     * @param action       the auth action
     * @param superAdminId the actor; nullable for failed logins where the email did not resolve
     * @param details      free-form details serialized to JSONB (e.g., attempted email, lockedUntil)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthEvent(AuditAction action, UUID superAdminId, Object details) {
        try {
            SuperAdminAuditLogEntity entry = SuperAdminAuditLogEntity.builder()
                    .superAdminId(superAdminId)
                    .action(action.getValue())
                    .entityType("SUPER_ADMIN")
                    .entityId(superAdminId)
                    .newValue(serializeToJson(details))
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .build();
            superAdminAuditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("SUPER_ADMIN AUDIT WRITE FAILED — action={}, superAdminId={}", action, superAdminId, e);
        }
    }

    /**
     * Tenant-targeted mutation by a super admin (provision, suspend,
     * reactivate, config update). Participates in the surrounding
     * transaction so the audit row rolls back with the mutation if
     * anything later in the same transaction fails.
     */
    @Transactional
    public void logTenantMutation(AuditAction action, UUID tenantId, Object oldValue, Object newValue) {
        try {
            SuperAdminAuditLogEntity entry = SuperAdminAuditLogEntity.builder()
                    .superAdminId(currentSuperAdminId())
                    .tenantId(tenantId)
                    .action(action.getValue())
                    .entityType("TENANT")
                    .entityId(tenantId)
                    .oldValue(serializeToJson(oldValue))
                    .newValue(serializeToJson(newValue))
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .build();
            superAdminAuditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("SUPER_ADMIN AUDIT WRITE FAILED — action={}, tenantId={}", action, tenantId, e);
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private UUID currentSuperAdminId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID) {
                return (UUID) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.trace("Could not extract super admin id from SecurityContext", e);
        }
        return null;
    }

    private String extractIpAddress() {
        try {
            HttpServletRequest request = currentRequest();
            if (request == null) return null;
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserAgent() {
        try {
            HttpServletRequest request = currentRequest();
            if (request == null) return null;
            String ua = request.getHeader("User-Agent");
            return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeToJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize super-admin audit value: {}", value.getClass().getSimpleName(), e);
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
