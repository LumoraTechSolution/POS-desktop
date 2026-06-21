# Step 30: Post-Merge Debugging and Fixes

## Objective

Identify and resolve the 403 Forbidden and 500 Internal Server Errors encountered after merging the user management and reports features to the development branch.

## Context

After merging `feature/user-management-and-reports` into `development`, the system experienced:

1. **401/403 Errors**: Users were unable to log in or access protected endpoints.
2. **500 Errors**: Critical report endpoints (`/api/v1/reports/sales`, `/api/v1/reports/inventory-valuation`) and the users list (`/api/v1/users`) were failing.

## Root Cause Analysis

1. **Disabled Admin User**: The seed admin user `admin@demo.lumora.com` was marked as `is_active = FALSE`, likely due to testing the toggle-status feature in previous a session.
2. **ClassCastException in Reports**: In the `ReportService`, raw JPA results from `getInventoryValuationByCategory` were being cast directly to `Long`. Depending on the SQL results, Hibernate sometimes returns `BigDecimal` for `SUM` operations, which caused a runtime exception.
3. **JPQL Syntax Issue**: The `ProductRepository` used a non-standard `bigdecimal` cast in its `@Query` annotation, which could lead to parsing failures in some environments.
4. **Unhandled DisabledException**: `org.springframework.security.authentication.DisabledException` was not caught by the `GlobalExceptionHandler`, causing it to bubble up as a generic 500 error instead of a descriptive 401/403.

## Changes Made

### 1. Database Fix

Created `V12__re_enable_admin.sql` to force the admin users (`admin@demo.lumora.com` and `admin@lumora.com`) to `is_active = TRUE`.

### 2. Backend Code Hardening

- **`ReportService.java`**: Implemented safer numeric extraction for `Object[]` result sets using `instanceof Number` checks.
- **`ProductRepository.java`**: Refactored the inventory valuation query to use standard JPQL joins and removed explicit casting.
- **`GlobalExceptionHandler.java`**: Added an explicit handler for `DisabledException` to return a `401 Unauthorized` with a clear message.

### 3. Server Restart

Restarted the Spring Boot application to apply the new migration and code changes.

## Verification Results

- **Auth**: `POST /api/v1/auth/login` successful with `admin@demo.lumora.com`.
- **User Management**: `GET /api/v1/users` returns 200 with the full list of employees.
- **Inventory Valuation**: `GET /api/v1/reports/inventory-valuation` returns 200 with accurate category-wise breakdown.
- **Sales Report**: `GET /api/v1/reports/sales` returns 200 with the history of transactions.

## Next Steps

- Continue testing the User Management UI to ensure the "Toggle Status" works correctly without locking out all admins.
- Verify multi-tenant isolation remains intact after the merge.
