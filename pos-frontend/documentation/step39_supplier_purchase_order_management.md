# Step 39: Supplier & Purchase Order (PO) Management

## Objective

Implement Supplier and Purchase Order Management to track active vendors and seamlessly route incoming inventory purchases through a managed status pipeline into the physical inventory.

## Components Built

### 1. Database Schema (`V18__add_suppliers_and_pos.sql`)

- Created `suppliers` table for managing vendor rolodex, linked via `tenant_id`.
- Created `purchase_orders` to track PO headers (Number, branch, supplier, status, date, total cost).
- Created `purchase_order_items` mapping Products to POs with ordered vs received quantities.
- Applied relevant Foreign Keys and indexing.
- Associated optional `supplier_id` directly to `products`.

### 2. Backend Architecture

#### Entities & Repositories

- `SupplierEntity`, `PurchaseOrderEntity`, `PurchaseOrderItemEntity`.
- Custom JpaRepository queries implemented filtering by `tenant_id`, `supplier_id`, and search string lookups.

#### Services & DTOs

- `SupplierService`: Maps standard CRUD capabilities matching the vendor definitions. Configured a soft-deletion `isActive` flag in lieu of hard db deletes to preserve financial constraints.
- `PurchaseOrderService`: Implements a rigid PO status pipeline (`DRAFT` → `ORDERED` → `PARTIAL` → `RECEIVED` → `CANCELLED`).
  - Implemented `receivePurchaseOrder()` tracking exact incoming partial/full product shipments against `purchase_order_items`.
  - Added cross-service hooks triggering `productService.updateStockForBranch()` when inventory moves to `RECEIVED`.

#### Controllers

- Secured API endpoints for `SupplierController` and `PurchaseOrderController` restricting roles to `ADMIN`, `MANAGER`, and `INVENTORY_MANAGER`.
- Enabled pagination and search parameters.

### 3. Frontend Implementation

#### APIs Integration

- Expanded `supplierService.ts` and `purchaseOrderService.ts` using `axios` wrapper matching backend DTO schemas.

#### UI Components

- Integrated shadcn-ui elements (`badge`, `select`, `scroll-area`, `popover`, `dialog`).
- **Suppliers Page** (`/inventory/suppliers`): Table displaying active/inactive vendors with a modal modifying data seamlessly.
- **Purchase Orders Page** (`/inventory/purchase-orders`):
  - Real-time tracker for ordered goods categorized by status badge.
  - Quick action buttons to progress POs physically through `DRAFT` via `ORDERED`.
  - **CreatePOModal**: Wizard attaching Suppliers to target receiving Branches mapped directly to selectable active product SKU rows with editable standard costs.
  - **ReceivePOModal**: An execution portal calculating remaining unfilled portions of an order granting staff immediate click-to-receive functionality updating branch stock automatically via caching validations.

## Completion State

- Backend compilation successful.
- Endpoints successfully bound to the client UI layer.
- Testing successfully captures items ordered translating directly into Branch Stock count bumps tracked by the POS application.
