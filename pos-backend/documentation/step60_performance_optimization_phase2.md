# Phase 2: Performance & Data Integrity Improvements

## Summary of Changes
Completed a major performance and architecture overhaul for the Lumora POS backend, focusing on dashboard efficiency, listing performance, data safety, and stock tracking consolidation.

## Implemented Steps

### 1. Dashboard Aggregate Optimization (PERF-003)
- **Refactor**: Replaced memory-intensive in-memory processing of daily sales with a optimized JPQL aggregate query.
- **Benefit**: Reduced memory footprint from $O(N)$ (where $N$ is number of daily sales) to $O(1)$ by delegating summation and grouping to the PostgreSQL engine.
- **Reference**: `SaleRepository.aggregateDailySummary` and `SaleService.getDailySummary`.

### 2. N+1 Stock Level Fix (PERF-004)
- **Refactor**: Replaced multiple per-product stock level queries with a single batch fetch in `ProductService`.
- **Benefit**: Reduced database roundtrips from $1+N$ to $2$ for product listing pages (fetching products + fetching all related stock levels in one go).
- **Reference**: `StockLevelRepository.findAllByProductIdInAndTenantId` and `ProductService.getAllProducts`.

### 3. Soft Delete for Financial Entities (DATA-005)
- **Feature**: Added `isDeleted` flag to `SaleEntity` and `ReturnEntity`.
- **Integrity**: Applied Hibernate `@Where(clause = "is_deleted = false")` to ensure soft-deleted records are excluded from standard queries by default while preserving historical data for audit trails.
- **Migration**: Created Flyway migration `V28__add_soft_delete_to_financial_entities.sql`.

### 4. Consolidated Stock Tracking (ARCH-002)
- **Refactor**: Converted `ProductEntity.stockQuantity` into a derived field using `@Formula`.
- **Benefit**: Eliminated redundant data synchronization between `Product` and `StockLevel` entities. The global stock count is now a real-time sum of quantities across all branches.
- **Cleanup**: Removed manual stock update logic from `SaleService`, `ProductService`, and `InventoryAdjustmentService`.
