package com.lumora.pos.common.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Body for bulk activate/deactivate endpoints: a set of ids + the target state. */
@Data
public class BulkStatusRequest {

    @NotEmpty(message = "ids must not be empty")
    private List<UUID> ids;

    private boolean active;
}
