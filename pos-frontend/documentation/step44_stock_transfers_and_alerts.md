# Step 44: Multi-Branch Stock Transfers & Low Stock Alerts

**Status:** In Progress
**Date:** 2026-03-09

## Overview

Implement inter-branch stock transfer functionality and automated low-stock threshold alerts. This completes the multi-location logistics loop started in Step 32 (Multi-Location Inventory).

---

## Implementation Steps

### Step 1: Backend — Stock Transfer Entity & Migration

- Create `StockTransferEntity` JPA entity with fields:
  - `sourceBranch` (ManyToOne → BranchEntity)
  - `destinationBranch` (ManyToOne → BranchEntity)
  - `product` (ManyToOne → ProductEntity)
  - `quantity` (Integer)
  - `status` (Enum: PENDING, IN_TRANSIT, COMPLETED, CANCELLED)
  - `notes` (String)
  - `transferredAt` (LocalDateTime — when marked COMPLETED)
- Create Flyway migration `V23__add_stock_transfers.sql`
- Extends `BaseEntity` (inherits id, tenantId, audit fields, version)

### Step 2: Backend — Stock Transfer DTOs

- `StockTransferRequest`: sourceBranchId, destinationBranchId, productId, quantity, notes
- `StockTransferResponse`: all fields + branch names + product name + SKU

### Step 3: Backend — Stock Transfer Repository

- `StockTransferRepository` with queries:
  - `findAllByTenantId(UUID tenantId, Pageable pageable)`
  - `findByStatusAndTenantId(TransferStatus status, UUID tenantId, Pageable pageable)`
  - `findAllBySourceBranchIdOrDestinationBranchId(UUID branchId, UUID branchId2)`

### Step 4: Backend — Stock Transfer Service

- `createTransfer()`: Validates branches, product, and source stock availability. Creates transfer with PENDING status.
- `completeTransfer()`: Deducts stock from source branch, adds to destination branch using existing `ProductService.updateStockForBranch()`. Creates TRANSFER_OUT and TRANSFER_IN adjustment records. Sets status to COMPLETED.
- `cancelTransfer()`: Only if status is PENDING or IN_TRANSIT. Sets status to CANCELLED.
- `getTransfers()`: Paginated list with optional status filter.

### Step 5: Backend — Stock Transfer Controller

- `POST /api/v1/stock-transfers` — Create transfer (ADMIN, MANAGER, INVENTORY_MANAGER)
- `GET /api/v1/stock-transfers` — List transfers with pagination + status filter
- `PUT /api/v1/stock-transfers/{id}/complete` — Complete a transfer
- `PUT /api/v1/stock-transfers/{id}/cancel` — Cancel a transfer

### Step 6: Backend — Low Stock Alert Endpoint

- `GET /api/v1/products/low-stock?branchId={optional}` — Returns products where branch stock ≤ lowStockThreshold
- Query the `stock_levels` table joined with `products` to compare quantity vs threshold

### Step 7: Frontend — Stock Transfer Service

- Create `stockTransferService.ts` with API calls matching the backend endpoints

### Step 8: Frontend — Stock Transfers Page

- New page at `/inventory/stock-transfers`
- Table listing all transfers with status badges (PENDING=yellow, IN_TRANSIT=blue, COMPLETED=green, CANCELLED=red)
- "New Transfer" button → modal with source branch, destination branch, product, quantity
- Action buttons to Complete or Cancel pending transfers

### Step 9: Frontend — Low Stock Alert Widget

- Dashboard widget showing products below their threshold
- Filterable by branch
- Quick visual with red/amber indicators

### Step 10: Frontend — Navigation & Integration

- Add "Stock Transfers" link to dashboard sidebar under inventory section
- Add low stock widget to the Overview dashboard page

---

## Architecture Notes

- Stock transfers leverage the existing `InventoryAdjustmentEntity.AdjustmentType.TRANSFER_IN / TRANSFER_OUT` types
- Uses existing `ProductService.updateStockForBranch()` for atomic stock operations
- All operations are tenant-scoped via `BaseEntity.tenantId`
- Optimistic locking via `@Version` prevents concurrent transfer conflicts
