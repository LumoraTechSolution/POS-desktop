package com.lumora.pos.employee.service;

import com.lumora.pos.cashsession.repository.CashSessionRepository;
import com.lumora.pos.common.exception.ResourceNotFoundException;
import com.lumora.pos.employee.dto.TimeRecordResponse;
import com.lumora.pos.employee.entity.TimeRecord;
import com.lumora.pos.employee.repository.TimeRecordRepository;
import com.lumora.pos.auth.entity.UserEntity;
import com.lumora.pos.auth.repository.UserRepository;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimeClockService {

    private final TimeRecordRepository timeRecordRepository;
    private final UserRepository userRepository;
    private final CashSessionRepository cashSessionRepository;

    @Transactional
    public TimeRecordResponse clockIn(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Optional<TimeRecord> existingRecord = timeRecordRepository.findActiveRecordByUserId(userId);
        if (existingRecord.isPresent()) {
            throw new com.lumora.pos.common.exception.BusinessException("User is already clocked in");
        }

        TimeRecord record = new TimeRecord();
        record.setUser(user);
        record.setTenantId(user.getTenantId());
        record.setClockInTime(LocalDateTime.now());

        TimeRecord savedRecord = timeRecordRepository.save(record);
        return mapToResponse(savedRecord);
    }

    @Transactional
    public TimeRecordResponse clockOut(UUID userId, String notes) {
        // Don't let the legacy clock-out leave an orphan OPEN cash session behind.
        // Cashiers must close the drawer via the cash-session end flow instead.
        if (cashSessionRepository.findActiveByUserId(userId).isPresent()) {
            throw new com.lumora.pos.common.exception.BusinessException(
                    "An open cash session exists for this user. End the shift from the terminal to reconcile the drawer first.");
        }

        TimeRecord activeRecord = timeRecordRepository.findActiveRecordByUserId(userId)
                .orElseThrow(() -> new com.lumora.pos.common.exception.BusinessException("User is not currently clocked in"));

        activeRecord.setClockOutTime(LocalDateTime.now());
        if (notes != null && !notes.trim().isEmpty()) {
            activeRecord.setNotes(notes);
        }

        TimeRecord savedRecord = timeRecordRepository.save(activeRecord);
        return mapToResponse(savedRecord);
    }

    @Transactional(readOnly = true)
    public TimeRecordResponse getStatus(UUID userId) {
        return timeRecordRepository.findActiveRecordByUserId(userId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<TimeRecordResponse> getUserHistory(UUID userId, Pageable pageable) {
        return timeRecordRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<TimeRecordResponse> getAllHistory(
            LocalDateTime from, LocalDateTime to, String status, String search, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();

        // Build a pre-computed lowercase LIKE pattern in Java rather than letting JPQL
        // build it via CONCAT. PostgreSQL fails with "function lower(bytea) does not exist"
        // when a NULL string parameter is fed into CONCAT/LOWER because the driver binds
        // unknown NULL as bytea. Empty string = "no filter".
        String searchPattern = (search == null || search.isBlank())
                ? ""
                : "%" + search.toLowerCase() + "%";
        String normalizedStatus = (status == null || status.isBlank()) ? "" : status;

        // Default dates so the IS NULL branches never need to fire — keeps the query
        // simple and avoids PostgreSQL type-inference quirks for null timestamps.
        LocalDateTime fromDate = (from == null) ? LocalDateTime.of(1970, 1, 1, 0, 0, 0) : from;
        LocalDateTime toDate   = (to == null)   ? LocalDateTime.of(2099, 12, 31, 23, 59, 59) : to;

        return timeRecordRepository.findAllByTenantIdFiltered(
                tenantId, fromDate, toDate, normalizedStatus, searchPattern, pageable)
                .map(this::mapToResponse);
    }

    private TimeRecordResponse mapToResponse(TimeRecord entity) {
        TimeRecordResponse response = new TimeRecordResponse();
        response.setId(entity.getId());
        response.setUserId(entity.getUser().getId());
        response.setUserName(entity.getUser().getFirstName() + " " + entity.getUser().getLastName());
        String roles = entity.getUser().getRoles().stream()
                .map(r -> r.getName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("NO_ROLE");
        response.setUserRole(roles);
        response.setClockInTime(entity.getClockInTime());
        response.setClockOutTime(entity.getClockOutTime());
        response.setNotes(entity.getNotes());

        if (entity.getClockOutTime() != null) {
            Duration duration = Duration.between(entity.getClockInTime(), entity.getClockOutTime());
            response.setDurationMinutes(duration.toMinutes());
        }

        return response;
    }
}
