# Step 58: Platform Audit Log Search & Filtering

## Overview
This step implements a high-performance search and filtering engine for the Super Admin Platform Audit Log. It allows system administrators to instantly filter across millions of security events by action type (e.g., CREATE, LOGIN) or targeted entity types (e.g., PRODUCT, TENANT) across all tenants.

## Key Changes
1. **Backend Repository Enhancement (`AuditLogRepository.java`)**
   - Implemented a custom JPQL `@Query` for global search.
   - Added `searchGlobalAuditLogs` which performs case-insensitive `LIKE` lookups across the `action` and `entityType` columns.
   - Designed the query with a null-safe `:search` parameter to handle optional filtering.

2. **Backend Service & Controller Layer (`SuperAdminAuditService.java` & `SuperAdminAuditController.java`)**
   - Updated `getGlobalAuditLogs` to accept an optional `search` string.
   - Dynamically switches between `findAll` and the new search repository method based on the presence of a query.
   - Exposed the `search` parameter to the REST controller via `@RequestParam`.

3. **Frontend API Integration (`superAdminAuditService.ts`)**
   - Refined the `getGlobalAuditLogs` method to handle the new `search` query parameter, properly sanitizing it with `encodeURIComponent`.

4. **Frontend UI Implementation (`AuditLogPage.tsx`)**
   - Built a sleek, responsive Search Input in the Header area with a `lucide-react` search icon.
   - Implemented an efficient **Debounce Logic (500ms)** to prevent excessive API calls while the user is typing.
   - Added automatic page-reset (to Page 0) upon searching to ensure the user always sees the most relevant results first.
   - Restored the missing `size` state and fixed linting errors to maintain production-grade code stability.

## Features
- **Real-time Filter**: Type "LOGIN" to see only authentication events across all tenants.
- **Entity Filter**: Type "PRODUCT" or "TENANT" to track specific lifecycle events.
- **Performance Optimized**: Uses server-side pagination combined with the search query to keep the UI snappy even with massive audit trails.

5. **Date-Range Filtering (Added)**
   - **Repository Level**: Extended the JPQL query with `CAST(:startDate AS timestamp)` checks to filter `createdAt` timestamps.
   - **Service & Controller**: Updated to handle `LocalDateTime` objects using Spring's `ISO.DATE_TIME` format.
   - **Frontend UI**: Integrated a sleek "From/To" date picker group into the header with a "Clear" utility button.
   - **Real-time Refresh**: The table automatically refreshes whenever a date is picked, ensuring instantaneous results.

