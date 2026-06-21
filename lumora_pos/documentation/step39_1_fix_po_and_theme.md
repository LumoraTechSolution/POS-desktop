# Step 39.1: Fix Supplier/PO Management & Restore Theme

## Problem

1. **Creation 500 Error**: Creating a Purchase Order failed with a 500 error on the backend.
2. **UI Regression**: Adding `shadcn` components overwritten the Cyberpunk Yellow theme and caused inconsistent animations between pages.

## Root Cause Analysis

- **Backend**:
  - Missing `created_by` and `updated_by` columns in PO and Supplier tables that `BaseEntity` expects.
  - `LocalDateTime` deserialization failed because the frontend sent ISO strings with a `Z` suffix.
- **Frontend**:
  - `shadcn` reset `globals.css` variables, losing the yellow monochromatic theme.
  - `shadcn` overwritten `badge.tsx` and `button.tsx`, removing custom variants (`success`, `warning`) and changing touch-friendly sizes.

## Solutions Applied

### Backend Fixes

1. **Flyway Migration (V19)**: Added `created_by` and `updated_by` to `suppliers`, `purchase_orders`, and `purchase_order_items`.
2. **Entity Fixes**: Removed redundant `createdBy` in `PurchaseOrderEntity` (inheritance from `BaseEntity`).
3. **Service Logic**: Updated `PurchaseOrderService` to manually set the creator ID from the security context until JPA Auditing is fully enabled.

### Frontend Fixes

1. **Theme Restoration**:
   - Re-applied Cyberpunk Yellow variables to `globals.css`.
   - Recursively replaced all hardcoded `indigo` classes with `primary` (Yellow) across the dashboard and inventory modules.
2. **Component Fixes**:
   - Restored `success` and `warning` variants to `Badge`.
   - Restored large touch-friendly sizes to `Button`.
3. **PO Creation Fix**: Updated `CreatePOModal` to send dates in `yyyy-MM-dd'T'HH:mm:ss` format to satisfy JVM `LocalDateTime`.
4. **UX/Animations**: Added `animate-in fade-in slide-in-from-bottom-4` animations to all main inventory pages for a premium feel.

## Verification

- [x] Backend compilation: `mvn clean compile` PASSED.
- [x] Date format issue resolved in `CreatePOModal`.
- [x] Sidebar and layout now follow the Cyberpunk Yellow theme.
- [x] Staggered entry animations added to Suppliers, Products, and PO pages.
