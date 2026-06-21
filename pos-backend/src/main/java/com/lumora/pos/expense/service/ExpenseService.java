package com.lumora.pos.expense.service;

import com.lumora.pos.branch.entity.BranchEntity;
import com.lumora.pos.branch.repository.BranchRepository;
import com.lumora.pos.branch.service.BranchAccessGuard;
import com.lumora.pos.common.exception.BusinessException;
import com.lumora.pos.expense.dto.ExpenseDtos.*;
import com.lumora.pos.expense.entity.ExpenseCategoryEntity;
import com.lumora.pos.expense.entity.ExpenseEntity;
import com.lumora.pos.expense.repository.ExpenseCategoryRepository;
import com.lumora.pos.expense.repository.ExpenseRepository;
import com.lumora.pos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final BranchRepository branchRepository;
    private final BranchAccessGuard branchAccessGuard;

    private static final List<String> DEFAULT_CATEGORIES =
            List.of("Rent", "Payroll", "Utilities", "Marketing", "Maintenance", "Other");

    // ─── Categories ─────────────────────────────────────────

    @Transactional
    public List<ExpenseCategoryResponse> getAllCategories() {
        UUID tenantId = TenantContext.getTenantId();
        ensureDefaults(tenantId);
        return categoryRepository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .map(this::mapCategory)
                .toList();
    }

    @Transactional
    public ExpenseCategoryResponse createCategory(ExpenseCategoryRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        if (categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, request.getName().trim())) {
            throw new BusinessException("A category named '" + request.getName() + "' already exists");
        }
        ExpenseCategoryEntity entity = ExpenseCategoryEntity.builder()
                .name(request.getName().trim())
                .isActive(request.isActive())
                .build();
        entity.setTenantId(tenantId);
        return mapCategory(categoryRepository.save(entity));
    }

    @Transactional
    public ExpenseCategoryResponse updateCategory(UUID id, ExpenseCategoryRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        ExpenseCategoryEntity entity = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Category not found"));
        entity.setName(request.getName().trim());
        entity.setActive(request.isActive());
        return mapCategory(categoryRepository.save(entity));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        ExpenseCategoryEntity entity = categoryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Category not found"));
        if (expenseRepository.existsByCategory_IdAndTenantId(id, tenantId)) {
            throw new BusinessException("Cannot delete a category that has expenses. Deactivate it instead.");
        }
        categoryRepository.delete(entity);
    }

    // ─── Expenses ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getExpenses(LocalDate start, LocalDate end, UUID branchId, Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        Optional<Set<UUID>> branchFilter = branchAccessGuard.reportBranchFilter(branchId);
        boolean ranged = start != null && end != null;

        Page<ExpenseEntity> page;
        if (branchFilter.isPresent()) {
            Set<UUID> branchIds = branchFilter.get();
            page = ranged
                    ? expenseRepository.findAllByTenantIdAndBranch_IdInAndExpenseDateBetweenOrderByExpenseDateDesc(
                            tenantId, branchIds, start, end, pageable)
                    : expenseRepository.findAllByTenantIdAndBranch_IdInOrderByExpenseDateDesc(tenantId, branchIds, pageable);
        } else {
            page = ranged
                    ? expenseRepository.findAllByTenantIdAndExpenseDateBetweenOrderByExpenseDateDesc(tenantId, start, end, pageable)
                    : expenseRepository.findAllByTenantIdOrderByExpenseDateDesc(tenantId, pageable);
        }
        return page.map(this::mapExpense);
    }

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        ExpenseCategoryEntity category = categoryRepository.findByIdAndTenantId(request.getCategoryId(), tenantId)
                .orElseThrow(() -> new BusinessException("Category not found"));
        ExpenseEntity entity = ExpenseEntity.builder()
                .category(category)
                .amount(request.getAmount())
                .expenseDate(request.getExpenseDate())
                .payee(trimToNull(request.getPayee()))
                .paymentMethod(trimToNull(request.getPaymentMethod()))
                .reference(trimToNull(request.getReference()))
                .notes(trimToNull(request.getNotes()))
                .recurring(request.isRecurring())
                .recurringInterval(trimToNull(request.getRecurringInterval()))
                .branch(resolveBranch(request.getBranchId(), tenantId))
                .build();
        entity.setTenantId(tenantId);
        return mapExpense(expenseRepository.save(entity));
    }

    @Transactional
    public ExpenseResponse updateExpense(UUID id, ExpenseRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        ExpenseEntity entity = expenseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Expense not found"));
        if (!entity.getCategory().getId().equals(request.getCategoryId())) {
            ExpenseCategoryEntity category = categoryRepository.findByIdAndTenantId(request.getCategoryId(), tenantId)
                    .orElseThrow(() -> new BusinessException("Category not found"));
            entity.setCategory(category);
        }
        entity.setAmount(request.getAmount());
        entity.setExpenseDate(request.getExpenseDate());
        entity.setPayee(trimToNull(request.getPayee()));
        entity.setPaymentMethod(trimToNull(request.getPaymentMethod()));
        entity.setReference(trimToNull(request.getReference()));
        entity.setNotes(trimToNull(request.getNotes()));
        entity.setRecurring(request.isRecurring());
        entity.setRecurringInterval(trimToNull(request.getRecurringInterval()));
        entity.setBranch(resolveBranch(request.getBranchId(), tenantId));
        return mapExpense(expenseRepository.save(entity));
    }

    @Transactional
    public void deleteExpense(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        ExpenseEntity entity = expenseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("Expense not found"));
        expenseRepository.delete(entity);
    }

    // ─── Helpers ────────────────────────────────────────────

    private void ensureDefaults(UUID tenantId) {
        if (categoryRepository.countByTenantId(tenantId) > 0) return;
        for (String name : DEFAULT_CATEGORIES) {
            ExpenseCategoryEntity c = ExpenseCategoryEntity.builder().name(name).isActive(true).build();
            c.setTenantId(tenantId);
            categoryRepository.save(c);
        }
    }

    /** Resolves and access-checks an optional branch tag; null = company-wide overhead. */
    private BranchEntity resolveBranch(UUID branchId, UUID tenantId) {
        if (branchId == null) return null;
        branchAccessGuard.assertCanAccess(branchId);
        return branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new BusinessException("Branch not found"));
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ExpenseCategoryResponse mapCategory(ExpenseCategoryEntity e) {
        return ExpenseCategoryResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .isActive(e.isActive())
                .build();
    }

    private ExpenseResponse mapExpense(ExpenseEntity e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .categoryId(e.getCategory().getId())
                .categoryName(e.getCategory().getName())
                .amount(e.getAmount())
                .expenseDate(e.getExpenseDate())
                .payee(e.getPayee())
                .paymentMethod(e.getPaymentMethod())
                .reference(e.getReference())
                .notes(e.getNotes())
                .recurring(e.isRecurring())
                .recurringInterval(e.getRecurringInterval())
                .branchId(e.getBranch() != null ? e.getBranch().getId() : null)
                .branchName(e.getBranch() != null ? e.getBranch().getName() : null)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
