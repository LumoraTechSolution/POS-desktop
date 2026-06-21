package com.lumora.pos.expense.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class ExpenseDtos {

    @Data
    public static class ExpenseCategoryRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @JsonProperty("isActive")
        private boolean isActive = true;
    }

    @Data
    @Builder
    public static class ExpenseCategoryResponse {
        private UUID id;
        private String name;
        @JsonProperty("isActive")
        private boolean isActive;
    }

    @Data
    public static class ExpenseRequest {
        @NotNull
        private UUID categoryId;

        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        private BigDecimal amount;

        @NotNull
        private LocalDate expenseDate;

        @Size(max = 255)
        private String payee;

        @Size(max = 30)
        private String paymentMethod;

        @Size(max = 100)
        private String reference;

        @Size(max = 2000)
        private String notes;

        private boolean recurring;

        @Size(max = 20)
        private String recurringInterval;

        /** Optional branch this cost belongs to. Null = company-wide overhead. */
        private UUID branchId;
    }

    @Data
    @Builder
    public static class ExpenseResponse {
        private UUID id;
        private UUID categoryId;
        private String categoryName;
        private BigDecimal amount;
        private LocalDate expenseDate;
        private String payee;
        private String paymentMethod;
        private String reference;
        private String notes;
        private boolean recurring;
        private String recurringInterval;
        private UUID branchId;
        private String branchName;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
