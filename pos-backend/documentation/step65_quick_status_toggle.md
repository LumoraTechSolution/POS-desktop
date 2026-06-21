# Step 65: Quick Status Toggle for Inventory Management

## Feature Overview
Administrators can now enable or disable products directly from the Product List using a one-click toggle. This eliminates the need to open the full edit form for simple availability changes.

## Implementation Details

### Backend (Spring Boot)
1.  **Endpoint**: Added `PATCH /api/v1/products/{id}/status`.
2.  **Service**: Implemented `toggleStatus(UUID id)` in `ProductService`.
3.  **Audit**: Every status toggle is logged in the `AuditAction.UPDATE` trail under the `PRODUCT_STATUS` category.
4.  **Security**: Enforced `hasAnyRole('ADMIN', 'MANAGER', 'INVENTORY_MANAGER')`.

### Frontend (Next.js)
1.  **Service**: Added `toggleStatus` method to `inventoryService.ts`.
2.  **Component**: Integrated `@radix-ui/react-switch` into `ProductTable.tsx`.
3.  **UX**: 
    - Emerald green "Active" state.
    - Grayed out "Inactive" state.
    - Instant "Toast" notification feedback on change.
    - Optimistic UI updates via React Query cache invalidation.

## Verification
- **Compilation**: Backend `BUILD SUCCESS`.
- **UI**: Verified the Switch component renders and correctly toggles state via the `onCheckedChange` handler.

## Benefits
- **Operational Speed**: Dramatically reduces the time needed to take items off-sale or re-list them.
- **Enterprise Standards**: Maintained full auditability and role-based access control.
