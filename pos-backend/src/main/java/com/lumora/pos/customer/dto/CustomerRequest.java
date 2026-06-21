package com.lumora.pos.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerRequest {
    @NotBlank(message = "First name is required")
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String address;
}
