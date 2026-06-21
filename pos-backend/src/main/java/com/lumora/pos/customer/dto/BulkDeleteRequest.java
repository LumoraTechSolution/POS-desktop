package com.lumora.pos.customer.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkDeleteRequest {

    @NotEmpty(message = "ids must not be empty")
    private List<UUID> ids;
}
