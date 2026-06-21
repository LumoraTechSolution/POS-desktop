# Step 50 — Step 8: Frontend Super Admin Tenant Listing Table

## Summary
The global platform needs a centralized table for the Super Admin to monitor and oversee every tenant running on the SaaS layer. 

### 1. `superAdminTenantService.ts`
Added two foundational functions bridging the React frontend to the Java Spring Boot Backend:
- `listTenants`: Passes search parameter, active status toggle, and standard `(page, size)` objects into Next.js/React.
- `toggleTenantStatus`: Reaches out to the `/api/v1/super-admin/tenants/{tenantId}/{action}` endpoint that flips `is_active` to instantly suspend/block API access.

### 2. `SuperAdminTenantsPage` 
A complete view component located at `/super-admin/tenants`:
- Uses the unified Lumora UI language (minimal borders, rounded curves, structured whitespace) tailored for an Administration context.
- Provides immediate visual feedback indicating the Tier (Small Business, Medium, Enterprise) mapped natively to database enums (`TenantSummaryResponse.planTier`).
- Includes Lucide icons to denote maximum capabilities dynamically pulled down from `TenantConfigurationEntity` (Max Users, Max Locations, Max Products).
- Features full pagination hooks directly integrating with the robust backend JPA Pageable system.
- Includes a direct Action context menu allowing Super Admins to instantly "Suspend Access" or "Restore Access" for rogue or defaulting businesses, securing the SaaS platform lifecycle.

## Next Phase
Step 9 will handle the actual provisioning workflow (Tenant Creation UI), enabling the platform to accept a company name, domain, admin contact, and plan tier, orchestrating the global creation and system role cloning.
