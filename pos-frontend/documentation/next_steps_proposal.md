# Next Steps Proposal: Lumora Enterprise POS

Following the completion of core sales, user management, and basic reporting, we are moving into **Phase 2: Operations & Intelligence** and hardening the **Phase 1: Core Foundation**.

## Current Progress Check

- [x] Sales Transaction Engine (PAID)
- [x] User Management (RBAC & Status Toggle)
- [x] Basic Reporting (Daily Summary & Sales History)
- [x] Receipt Engine (Thermal formatting)
- [x] Multi-tenancy Architecture (Discriminator column)

## Proposed Next Steps

### Step 31: Bulk Product Import/Export (CSV/Excel)

- **Goal**: Allow enterprise users to onboard large catalogs quickly.
- **Backend**: Implement `BulkProductService` to parse uploaded files, validate data integrity, and perform batch inserts.
- **Frontend**: Add "Import" and "Export" buttons to the Inventory/Products management page with progress feedback.

### Step 32: Advanced Inventory: Multi-Location Support

- **Goal**: Enable tracking stock across multiple branches or warehouses.
- **Backend**: Create `Branch` entity and update `stockQuantity` to be location-specific (StockLevel table).
- **Frontend**: Branch selection in the Navbar and location-specific stock views.

### Step 33: Tax Configuration System

- **Goal**: Replace the hardcoded 10% tax with a configurable system.
- **Backend**: `TaxRate` entity and service to fetch applicable taxes based on location/category.
- **Frontend**: Settings page for managing tax rules.

### Step 34: Returns & Refunds Workflow

- **Goal**: Complete the sales lifecycle with full/partial refunds.
- **Backend**: Implement refund logic (adjusting sale status, restoring inventory, logging audit trails).
- **Frontend**: "Return Items" button in Sales History with a simplified return wizard.

### Step 35: Customer Relationship Management (CRM) V1

- **Goal**: Track customer purchase history and loyalty points.
- **Backend**: Finalize `Customer` service for full management.
- **Frontend**: Dedicated Customers page and Customer lookup at the POS Terminal.

---

**Please confirm which step you would like to proceed with first, or provide adjustments to the plan.**
