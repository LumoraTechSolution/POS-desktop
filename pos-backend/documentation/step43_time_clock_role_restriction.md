# Step 43: Time Clock Role-Based Restriction (Backend)

**Status:** Completed
**Date:** 2026-03-09

## Overview

Added `@PreAuthorize` role-based access control to the `TimeClockController` endpoints, restricting clock-in/out and status/history to non-ADMIN roles. ADMIN users can still view all employee timesheets via the existing `all-history` endpoint.

## Changes

### `TimeClockController.java`

Added `@PreAuthorize("hasAnyRole('CASHIER', 'MANAGER', 'INVENTORY_MANAGER')")` to the following endpoints:

| Endpoint                         | Method | Restriction Added                        |
| -------------------------------- | ------ | ---------------------------------------- |
| `/api/v1/time-clock/clock-in`    | POST   | CASHIER, MANAGER, INVENTORY_MANAGER only |
| `/api/v1/time-clock/clock-out`   | POST   | CASHIER, MANAGER, INVENTORY_MANAGER only |
| `/api/v1/time-clock/status`      | GET    | CASHIER, MANAGER, INVENTORY_MANAGER only |
| `/api/v1/time-clock/history`     | GET    | CASHIER, MANAGER, INVENTORY_MANAGER only |
| `/api/v1/time-clock/all-history` | GET    | ADMIN, MANAGER _(unchanged)_             |

## Security Behavior

- ADMIN users calling clock-in/out/status/history will receive **403 Forbidden**.
- ADMIN users can still access `/all-history` to view/manage employee timesheets.
- No changes to `TimeClockService` — restriction is enforced at the controller layer.

## Validation

- Endpoints return 403 for ADMIN role on restricted operations.
- No impact on existing CASHIER/MANAGER/INVENTORY_MANAGER functionality.
- `all-history` endpoint remains accessible to ADMIN and MANAGER.
