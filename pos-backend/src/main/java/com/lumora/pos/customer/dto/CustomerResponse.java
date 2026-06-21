package com.lumora.pos.customer.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CustomerResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String address;
    private Integer loyaltyPoints;
    private LocalDateTime createdAt;
}
