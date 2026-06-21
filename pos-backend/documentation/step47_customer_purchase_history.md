# Step 47: Customer Purchase History Page (Backend)

## Overview
Added the capability to fetch all previous sales associated with a specific customer, enabling the new Customer Profile dashboard in the frontend.

## Changes Implemented

### 1. `SaleRepository.java`
- Added new query: `findByCustomerIdAndTenantIdOrderByCreatedAtDesc`.
- This efficiently queries the sales table for all transactions tied to the `customerId`, paginated and sorted by newest first.

### 2. `SaleService.java`
- Added `getSalesByCustomer(UUID customerId, Pageable pageable)`.
- Method maps the `SaleEntity` list to `SaleResponse` DTOs, populating items and computed fields via `mapToResponse`.

### 3. `SalesController.java`
- Added new GET endpoint: `/api/v1/sales/customer/{customerId}`.
- Secured via `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")`.

## Next Steps
- Implement Bulk Product Import/Export (Task 3).
