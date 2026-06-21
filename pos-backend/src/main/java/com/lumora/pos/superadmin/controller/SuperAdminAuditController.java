package com.lumora.pos.superadmin.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.common.dto.PagedResponse;
import com.lumora.pos.superadmin.dto.SuperAdminAuditResponse;
import com.lumora.pos.superadmin.service.SuperAdminAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for Super Admin to view global audit logs.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/super-admin/audit")
@RequiredArgsConstructor
public class SuperAdminAuditController {

    private final SuperAdminAuditService superAdminAuditService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<SuperAdminAuditResponse>>> getGlobalAuditLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<SuperAdminAuditResponse> result = superAdminAuditService.getGlobalAuditLogs(
                search,
                action,
                startDate,
                endDate,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );

        PagedResponse<SuperAdminAuditResponse> pagedResponse = PagedResponse.<SuperAdminAuditResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    /**
     * Distinct action codes for the audit-log action multi-select.
     */
    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<List<String>>> getDistinctActions() {
        return ResponseEntity.ok(ApiResponse.success(superAdminAuditService.getDistinctActions()));
    }

    /**
     * Streams a CSV export honoring the same filter parameters as the
     * paginated read. The download Content-Disposition includes a
     * timestamp so repeated exports don't overwrite each other.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        String filename = "audit-log-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now()) + ".csv";

        StreamingResponseBody body = out -> superAdminAuditService.streamAuditLogCsv(
                search, action, startDate, endDate, out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}
