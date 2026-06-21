# Step 29: Role-Based UI Access Restrictions

## Objective

Restrict UI access to specific features based on user roles, specifically ensuring that `INVENTORY_MANAGER` (and other non-sales roles) cannot access or view the POS Terminal entry point.

## Context

As per the `POS_System_User_Requirements.md` (Section 2), the `INVENTORY_MANAGER` role is strictly designed for backend inventory management out of the POS terminal flow (e.g., managing products, conducting stock takes, adjusting stock levels, creating purchase orders). They should not be interacting with the actual sales terminal or checking out products.
While the backend (`SalesController`) already contains role protections blocking non-sales staff from calling sales endpoints (`@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")`), the frontend UI needed an update to reflect these permissions accurately and hide elements the user is not authorized to use.

## Changes Made

- **File:** `frontend/src/app/(dashboard)/layout.tsx`
  - **Action:** Added conditional rendering around the **"Open POS Terminal"** navigation link.
  - **Implementation:** The button now only renders if `user?.roles?.includes('ADMIN') || user?.roles?.includes('MANAGER') || user?.roles?.includes('CASHIER')` is true.

## Result

Users with roles other than `ADMIN`, `MANAGER`, or `CASHIER` (such as `INVENTORY_MANAGER`) will no longer see the "Open POS Terminal" button in their sidebar, strictly enforcing role boundaries within the UI and matching backend permissions.
