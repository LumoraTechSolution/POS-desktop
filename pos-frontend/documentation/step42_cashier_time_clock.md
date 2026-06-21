# Step 42: Cashier Time-Clock & Shifts

**Status:** Completed
**Date:** 2026-03-07

## Overview

Added an internal HR module for tracking employee time directly within the Lumora POS application, meeting Phase 2/3 enterprise requirements without needing an external HR application.

## Backend Implementation

1. **Entity `TimeRecord`**: Added a new entity mapping the `time_records` database table. It tracks:
   - `user_id` (foreign key)
   - `clock_in_time`
   - `clock_out_time`
   - `notes`
2. **Repository `TimeRecordRepository`**: Created JPA Repository with query `findActiveRecordByUserId` to fetch a record where `clock_out_time IS NULL`.
3. **Service `TimeClockService`**: Built logic encapsulating the clock-in/out logic.
   - Prevents double clock-ins
   - Prevents clock-out if not clocked in
   - Computes duration properly mapping into `TimeRecordResponse.java`.
4. **Controller `TimeClockController`**: Configured REST endpoints (`/api/v1/time-clock`) that utilize proper JWT Authentication through `authentication.getName()` UUID parsing.

## Frontend Implementation

1. **API Service**: Written in `timeClockService.ts` matching existing generic HTTP hooks using `@tanstack/react-query`.
2. **Component `TimeClockWidget.tsx`**: Built a universal state-aware React widget.
   - **Local Timing**: Calculates realtime elapsed time (`setInterval`) based on the fetched `clock_in_time` avoiding unnecessary server pings.
   - **Dynamic Styling**: Shifts to Emerald/Green tint when clocked in.
   - **Variants Mode**: Accepts `variant="sidebar"` (vertical stack) or `variant="header"` (horizontal strip).
3. **Integration**:
   - Injected into `/app/(dashboard)/layout.tsx` (Sidebar footer below navigation, above user profile).
   - Injected into `POSHeader.tsx` (Top right corner of the actual point-of-sale terminal).

## Admin & Management Features

1. **Admin Timesheets View (`/employees/timesheets`)**: Created a dedicated dashboard page for Admins/Managers to view all employee clock sessions.
2. **Backend API (`GET /api/v1/time-clock/all-history`)**: Added securely using `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")` and Spring Data JPA `findAll`.
3. **Frontend Datatable**: Integrated client-side sorting/filtering by name and role alongside native pageable backend requests.

## Bug Fixes & Refinements

1. **Flyway Migration Audit Columns**: Fixed application crash by executing a new migration `V22__fix_audit_columns_for_time_records.sql` to explicitly use `UUID` for `created_by` and `updated_by` columns expected by `BaseEntity`.
2. **Tenant ID Injection**: Fixed a 500 Internal Server Error (DataIntegrityViolation) by explicitly assigning the current user's `tenantId` during the `clockIn` service method.
3. **Pagination Type Map**: Fixed Frontend `TypeError` by correctly mapping the root `totalPages` variables coming natively from the Spring Data `@RestController` page object instead of nesting it under `.page`.

## Role-Based Clock-In Restriction

**Policy:** All roles except ADMIN can clock in/out. Admins can still view all employee timesheets.

| Role              | Can Clock In/Out | Can View Timesheets       |
| ----------------- | ---------------- | ------------------------- |
| CASHIER           | ✅ Yes           | Own history only          |
| MANAGER           | ✅ Yes           | Own + all employees       |
| INVENTORY_MANAGER | ✅ Yes           | Own history only          |
| ADMIN             | ❌ No            | All employees (view only) |

### Changes Made:

1. **Backend** (`TimeClockController.java`): Added `@PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'INVENTORY_MANAGER')")` to clock-in, clock-out, status, and personal history endpoints.
2. **Frontend Dashboard** (`layout.tsx`): `TimeClockWidget` hidden for ADMIN via `!user?.roles?.includes('ADMIN')` check.
3. **Frontend POS Terminal** (`POSHeader.tsx`): `TimeClockWidget` hidden for ADMIN via `userRole !== 'ADMIN'` check.

## Validation

- Server gracefully handles edge cases via IllegalStateException parsing into common `ApiResponse` errors.
- ADMIN users receive 403 Forbidden if they attempt to call clock-in/out endpoints directly.
- Visuals correctly match the application's Cyberpunk/Enterprise styling parameters.
- Database correctly associates records to specific user tenancies safely.
