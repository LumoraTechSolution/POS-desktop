package com.lumora.pos.expense.controller;

import com.lumora.pos.common.dto.ApiResponse;
import com.lumora.pos.expense.dto.ExpenseDtos.*;
import com.lumora.pos.expense.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ExpenseCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(expenseService.getAllCategories()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> createCategory(
            @Valid @RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.createCategory(request), "Category created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.updateCategory(id, request), "Category updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        expenseService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Category deleted").build());
    }
}
