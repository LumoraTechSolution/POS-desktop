# Step 32: Multi-Location Inventory Support

## Objective

Enable the system to track and manage inventory across multiple physical locations (branches, warehouses, or stores) per tenant. This is a key requirement for Phase 2: Operations & Intelligence.

## Planned Activities

### Phase 1: Persistence & Data Model

- [x] Create `BranchEntity` to represent physical locations.
- [x] Create `StockLevelEntity` to map products to specific branches with quantity tracking.
- [x] Implement database migration (Flyway V13) to:
  - Add `branches` and `stock_levels` tables.
  - Create a 'Default' branch for every existing tenant.
  - Migrate existing `products.stock_quantity` to `stock_levels` for the 'Default' branch.

### Phase 2: Backend Logic

- [x] Implement `BranchService` and `BranchController` for CRUD operations.
- [x] Update `SaleService` to require a `branchId` and deduct stock from the specific location.
- [ ] Implement `InventoryAdjustmentService` for manually moving stock or setting levels per branch.

### Phase 3: Frontend Integration

- [x] Create Branch management pages in the Admin Dashboard.
- [x] Add branch selection logic to the POS Terminal.
- [ ] Update Product forms and tables to show/edit stock levels per branch.

## Current Status

- [x] Task Breakdown
- [x] Backend Data Model
- [x] Database Migration
- [x] Multi-store Sale Logic
- [x] Frontend Implementation (Branches & POS)
