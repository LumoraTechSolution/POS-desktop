# Step 37: Returns and Refunds Workflow Implementation

## Overview

Successfully implemented a robust, enterprise-grade Returns and Refunds system. This workflow allows employees to process partial or full returns for paid transactions, restores inventory stock, and implements a manager approval process for high-value refunds.

## Activities Completed

### 1. Database Schema (`V16__add_returns_refunds.sql`)

- Created `returns` table to track header-level refund data (e.g., status, reason, total refund amount, manager approval).
- Created `return_items` table mapped via foreign keys to the original `sale_items`.
- Incorporated check constraints to enforce valid `status` ('PENDING', 'APPROVED', 'COMPLETED', 'REJECTED') and `refund_method` ('ORIGINAL', 'CASH', 'STORE_CREDIT').

### 2. Backend Implementation (Spring Boot)

- **Entities:** Developed `ReturnEntity` and `ReturnItemEntity` with JPA annotations.
- **Repositories:** `ReturnRepository` added for efficient data retrieval scoped by tenant ID and Sale ID.
- **DTOs:** Created robust request and response objects (`ReturnRequest`, `ReturnItemRequest`, `ReturnResponse`, etc.) to interface cleanly with the frontend.
- **Service (`ReturnService`):**
  - Validation: Prevents refunding more than was originally purchased.
  - Calculation: Proportionally computes the exact refund value per line item.
  - Stock Restoration: Upon 'COMPLETED' status, automatically re-adds the returned quantity back into inventory via `ProductService.updateStock()`.
  - Approval Workflow: Automatically flags refunds exceeding $500 as 'PENDING', requiring explicit manager/admin approval.
- **Controller (`ReturnController`):** REST API endpoints configured with tight RBAC logic. Cashiers can invoke returns, but only `MANAGER` and `ADMIN` roles can hit the `/approve` endpoint.

### 3. Frontend Integration (Next.js)

- **API Service (`returnService.ts`):** Created the React bridging logic.
- **Return Wizard (`ReturnModal.tsx`):**
  - Added a "Return" button on every row in the Sales History (Reports) screen.
  - Designed an intuitive modal that pulls the original invoice details.
  - Implements dynamic fields so users can enter exactly how many of each item they wish to return, capping inputs to the maximum remaining refundable quantity.
- **Returns History Page (`reports/page.tsx`):**
  - Added a third "Returns History" tab in the Reports module.
  - The table neatly displays all past returns.
  - Added Action buttons (`ShieldCheck` for Approve, `ShieldAlert` for Reject) that dynamically appear _only_ if the return is 'PENDING' and the logged-in user possesses elevated roles.

## Next Steps

- **User Validation:** The user should navigate to `Reports -> Sales History`, invoke a return, and verify that the items properly refund and restock within the system.
- Following verification, we will move toward Advanced Inventory or the CRM/Loyalty points feature based on the roadmap.
