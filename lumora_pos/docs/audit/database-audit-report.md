# Database Layer Audit Report
**Project:** Lumora POS System (Spring Boot 3 Multi-Tenant SaaS)  
**Date:** 2026-04-22  
**Auditor:** Claude Code  
**Scope:** Flyway migrations, JPA entities, transaction management, connection pooling, query performance

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 8     |
| HIGH     | 7     |
| MEDIUM   | 8     |
| LOW      | 5     |
| **Total**| **28**|

**Verdict: NOT production-ready.** Multiple critical foreign key constraints are missing across tenant-scoped tables, creating data integrity violations and multi-tenant isolation breaches.

---

## CRITICAL Findings

### C-1 — Missing FK on `sales` and `sale_items` (tenant_id)
- **File:** `backend/src/main/resources/db/migration/V9__sales_schema.sql` (lines 6–7)
- **Issue:** `sales.tenant_id` and `sale_items.tenant_id` have no `REFERENCES tenants(id) ON DELETE CASCADE` constraint.
- **Impact:** Orphaned sales data on tenant deletion; no referential integrity.

### C-2 — Missing FK on `customers` (tenant_id)
- **File:** `backend/src/main/resources/db/migration/V10__customer_schema.sql` (line 6)
- **Issue:** `customers.tenant_id` has no FK to `tenants(id)`.
- **Impact:** Orphaned customer records on tenant deletion; data integrity violation.

### C-3 — Missing FK on `returns` and `return_items` (tenant_id)
- **File:** `backend/src/main/resources/db/migration/V16__add_returns_refunds.sql` (lines 12, 49)
- **Issue:** Both tables have `tenant_id UUID NOT NULL` without FK constraints.
- **Impact:** Critical multi-tenant isolation breach; orphaned return records.

### C-4 — Missing FK on `suppliers`, `purchase_orders`, `purchase_order_items` (tenant_id)
- **File:** `backend/src/main/resources/db/migration/V18__add_suppliers_and_pos.sql` (lines 3, 19)
- **Issue:** All three tables missing `REFERENCES tenants(id)` on `tenant_id`.
- **Impact:** Orphaned supplier and PO records; potential tenant data leakage.

### C-5 — Missing FK on `time_records` (tenant_id)
- **File:** `backend/src/main/resources/db/migration/V20__Create_Time_Records_Table.sql` (line 3)
- **Issue:** `time_records.tenant_id` has no FK constraint to `tenants(id)`.
- **Impact:** Multi-tenant isolation breach.

### C-6 — Missing FK on `inventory_adjustments` (tenant_id)
- **File:** `backend/src/main/resources/db/migration/V14__add_inventory_adjustments.sql` (line 3)
- **Issue:** `inventory_adjustments.tenant_id` missing `REFERENCES tenants(id)`.
- **Impact:** Orphaned adjustment records; data integrity violation.

### C-7 — Missing FK on `stock_transfers` (tenant_id)
- **File:** `backend/src/main/resources/db/migration/V23__add_stock_transfers.sql` (line 7)
- **Issue:** `stock_transfers.tenant_id` missing FK to `tenants(id)`.
- **Impact:** Orphaned transfer records.

### C-8 — `BulkProductService.exportProducts()` Leaks All-Tenant Data
- **File:** `backend/src/main/java/com/lumora/pos/inventory/service/BulkProductService.java` (lines 155–159)
- **Issue:** Calls `productRepository.findAll()` with no tenant filter — returns ALL products across ALL tenants. Also causes N+1 queries on category/brand access.
- **Impact:** **Severe multi-tenant data leakage** + performance degradation.

---

## HIGH Findings

### H-1 — `FetchType.EAGER` on `UserEntity.roles` and `RoleEntity.permissions`
- **Files:**
  - `backend/src/main/java/com/lumora/pos/auth/entity/UserEntity.java` (line 50)
  - `backend/src/main/java/com/lumora/pos/auth/entity/RoleEntity.java` (line 32)
- **Issue:** `@ManyToMany(fetch = FetchType.EAGER)` triggers N+1 and cartesian product queries on every user/role load.
- **Fix:** Change to `FetchType.LAZY` and use `@EntityGraph` where join is needed.

### H-2 — `RefreshTokenRepository.findByToken()` Missing Tenant Scope
- **File:** `backend/src/main/java/com/lumora/pos/auth/repository/RefreshTokenRepository.java`
- **Issue:** Token lookups don't filter by `tenant_id`. A token from one tenant could potentially refresh another tenant's session.
- **Impact:** Cross-tenant authentication bypass risk.

### H-3 — Missing Index on `RefreshTokenEntity (tenantId, token)`
- **File:** `backend/src/main/java/com/lumora/pos/auth/entity/RefreshTokenEntity.java` (lines 21–47)
- **Issue:** No composite index on `(tenant_id, token)` or `(tenant_id, user_id)` for efficient tenant-scoped lookups.
- **Impact:** Slow token validation under load.

### H-4 — H2 Test Config Diverges from PostgreSQL
- **File:** `backend/src/test/resources/application-test.yml` (line 3)
- **Issue:** H2 `MODE=PostgreSQL` doesn't cover UUID generation, JSONB, full-text search, or sequence behavior.
- **Impact:** Tests pass but production fails — false confidence in test coverage.

### H-5 — ORM Cascade vs SQL `ON DELETE CASCADE` Conflict
- **Files:**
  - `backend/src/main/java/com/lumora/pos/purchase/entity/PurchaseOrderEntity.java` (line 56)
  - `backend/src/main/java/com/lumora/pos/sales/entity/SaleEntity.java` (line 52)
  - `backend/src/main/java/com/lumora/pos/returns/entity/ReturnEntity.java` (line 64)
- **Issue:** Entities use `CascadeType.ALL + orphanRemoval=true` while migrations also define `ON DELETE CASCADE`. Direct SQL deletes bypass JPA lifecycle — inconsistent state possible.

### H-6 — `purchase_orders.supplier_id` Nullable in DB but Not in Entity
- **File:** `backend/src/main/resources/db/migration/V18__add_suppliers_and_pos.sql` (line 27)
- **Issue:** Column missing `NOT NULL` in migration but declared `nullable = false` in `PurchaseOrderEntity`. Schema and ORM mapping are out of sync.
- **Impact:** Database allows NULL supplier_id which violates entity contract.

### H-7 — N+1 Query in `DashboardService.buildTopProducts()`
- **File:** `backend/src/main/java/com/lumora/pos/dashboard/service/DashboardService.java` (lines 125–137)
- **Issue:** Product names are fetched one-by-one in a loop via `findByIdAndTenantId()`. Should use batch `findAllById()`.
- **Impact:** Slow dashboard under load.

---

## MEDIUM Findings

### M-1 — N+1 in `ReturnService.mapItem()`
- **File:** `backend/src/main/java/com/lumora/pos/returns/service/ReturnService.java` (lines 361–364)
- **Issue:** Separate `productRepository.findById()` per return item. Should batch-fetch.

### M-2 — Missing Composite Index on `return_items(return_id, product_id)`
- **File:** `backend/src/main/resources/db/migration/V16__add_returns_refunds.sql` (lines 71–76)
- **Issue:** Individual indexes exist but no composite index for efficient cascade delete checks.

### M-3 — Missing Index on `(tenant_id, is_deleted, created_at)` for Sales
- **File:** `backend/src/main/resources/db/migration/V11__reporting_performance_indices.sql`
- **Issue:** Soft-delete filter (`is_deleted`) not covered by existing indexes. Full index scan on deleted records.

### M-4 — `refresh_tokens.token` Unique Constraint Not Tenant-Scoped
- **File:** `backend/src/main/resources/db/migration/V2__add_refresh_tokens.sql` (line 4)
- **Issue:** Unique constraint should be `UNIQUE (tenant_id, token)`, not just `UNIQUE (token)`.

### M-5 — Missing Index on `inventory_adjustments(tenant_id, created_by)`
- **File:** `backend/src/main/resources/db/migration/V14__add_inventory_adjustments.sql`
- **Issue:** No index for audit queries filtering by user within a tenant.

### M-6 — Soft-Delete Indexes Not Partial
- **File:** `backend/src/main/resources/db/migration/V28__add_soft_delete_to_financial_entities.sql`
- **Issue:** Index on `is_deleted` column alone is inefficient. Use partial indexes: `WHERE is_deleted = false`.

### M-7 — Missing `NOT NULL` on Additional FK Columns
- Multiple migration files omit `NOT NULL` on columns that are required per JPA entity definitions.

### M-8 — Missing Composite Index on `returns(tenant_id, status, created_at)`
- **File:** `backend/src/main/resources/db/migration/V16__add_returns_refunds.sql` (line 73)
- **Issue:** Current index insufficient for "pending returns ordered by date" queries.

---

## LOW Findings

### L-1 — Unindexed Cash Sales (`customer_id IS NULL`)
- Partial index on `customer_id` skips NULL (cash) sales. Slow queries for cash sale analysis.

### L-2 — Missing Index on `products.brand_id`
- `ProductEntity` has `@ManyToOne` on brand but no index in migration. Slow "products by brand" queries.

### L-3 — Missing `@Version` on `TimeRecord`
- `TimeRecord` is transactional (clock in/out) and should have optimistic locking via `@Version`.

### L-4 — `RefreshTokenEntity` Missing Audit Columns
- Does not extend `BaseEntity`; lacks `created_by` / `updated_by`. Reduced audit trail for token lifecycle.

### L-5 — Version + Soft-Delete Edge Case
- Records with both `version` and `is_deleted` can produce stale version exceptions if a soft-deleted record is restored.

---

## Recommended Fix Priority

### Immediate (Block Production)
1. Create `V30__fix_tenant_fk_constraints.sql` — add missing `REFERENCES tenants(id) ON DELETE CASCADE` to all affected tables (C-1 through C-7).
2. Fix `BulkProductService.exportProducts()` to filter by `TenantContext.getCurrentTenantId()` (C-8).
3. Add tenant scope to `RefreshTokenRepository.findByToken()` (H-2).

### High Priority (Before Launch)
4. Change `UserEntity.roles` and `RoleEntity.permissions` to `FetchType.LAZY` (H-1).
5. Add `NOT NULL` to `purchase_orders.supplier_id` via migration (H-6).
6. Batch-fetch in `DashboardService.buildTopProducts()` (H-7).

### Medium Priority (First Sprint Post-Launch)
7. Add partial indexes for soft-delete columns (M-6).
8. Add composite index `(tenant_id, is_deleted, created_at)` on sales (M-3).
9. Batch-fetch in `ReturnService.mapItem()` (M-1).
10. Scope `refresh_tokens` unique constraint to `(tenant_id, token)` (M-4).

---

## Files to Modify

| File | Action |
|------|--------|
| `db/migration/V30__fix_tenant_fk_constraints.sql` | **Create** — add all missing FK constraints |
| `db/migration/V31__fix_indexes_and_constraints.sql` | **Create** — add missing indexes and partial indexes |
| `inventory/service/BulkProductService.java` | Fix tenant filter + batch load |
| `auth/entity/UserEntity.java` | Change EAGER → LAZY |
| `auth/entity/RoleEntity.java` | Change EAGER → LAZY |
| `auth/repository/RefreshTokenRepository.java` | Add tenant_id to token lookup |
| `dashboard/service/DashboardService.java` | Batch product name lookup |
| `returns/service/ReturnService.java` | Batch product name lookup |
