# Step 34: Fix Inventory Adjustment History Not Displaying

## Date

2026-03-03

## Issue

The **History tab** in the Inventory Management modal (InventoryAdjustmentModal) showed nothing — always displaying "No adjustment history found" even when adjustments existed.

## Root Cause Analysis

Two issues in `InventoryAdjustmentService.getAdjustmentsByProduct()`:

### Issue 1: Missing `@Transactional(readOnly = true)`

The `mapToResponse` method accesses lazy-loaded relationships:

- `entity.getProduct().getName()` — `@ManyToOne(fetch = FetchType.LAZY)`
- `entity.getBranch().getName()` — `@ManyToOne(fetch = FetchType.LAZY)`

Without `@Transactional`, the Hibernate session closes after the repository call, causing a `LazyInitializationException` when mapping. This results in a 500 error that the frontend silently handles by showing the empty state.

### Issue 2: No Tenant Filtering (Security Gap)

The original repository method `findByProductIdOrderByCreatedAtDesc(UUID productId)` did not filter by `tenantId`, violating multi-tenant data isolation.

## Fix Applied

### File 1: `InventoryAdjustmentRepository.java`

Added tenant-scoped query method:

```diff
+List<InventoryAdjustmentEntity> findByProductIdAndTenantIdOrderByCreatedAtDesc(UUID productId, UUID tenantId);
```

### File 2: `InventoryAdjustmentService.java`

Added `@Transactional(readOnly = true)` and switched to tenant-scoped query:

```diff
+@Transactional(readOnly = true)
 public List<InventoryAdjustmentResponse> getAdjustmentsByProduct(UUID productId) {
-    return adjustmentRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
+    UUID tenantId = TenantContext.getTenantId();
+    return adjustmentRepository.findByProductIdAndTenantIdOrderByCreatedAtDesc(productId, tenantId).stream()
             .map(this::mapToResponse)
             .toList();
 }
```

## Affected Files

- `src/main/java/com/lumora/pos/inventory/repository/InventoryAdjustmentRepository.java`
- `src/main/java/com/lumora/pos/inventory/service/InventoryAdjustmentService.java`

## Verification

- ✅ `mvnw compile` completed successfully
- ✅ Backend restarted successfully

## Risk Assessment

- **Risk Level:** Medium (fixes lazy loading and multi-tenant security)
- **Regression Risk:** Low — read-only operation, no write-path changes
