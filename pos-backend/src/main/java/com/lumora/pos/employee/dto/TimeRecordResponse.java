package com.lumora.pos.employee.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TimeRecordResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userRole;
    private LocalDateTime clockInTime;
    private LocalDateTime clockOutTime;
    private String notes;
    private Long durationMinutes;
}
