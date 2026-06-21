package com.lumora.pos.branch.dto;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchRequest {
    @NotBlank(message = "Branch name is required")
    private String name;
    private String address;
    private String phoneNumber;
    @JsonProperty("isActive")
    private boolean isActive;
}
