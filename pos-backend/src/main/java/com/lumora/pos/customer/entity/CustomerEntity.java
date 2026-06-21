package com.lumora.pos.customer.entity;

import com.lumora.pos.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerEntity extends BaseEntity {

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    private String phone;

    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Builder.Default
    private Integer loyaltyPoints = 0;
}
