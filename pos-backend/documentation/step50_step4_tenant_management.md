# Step 50 — Step 4: Tenant Management Service & Controller

## Summary
The Super Admin module requires CRUD capabilities to manage tenants. Because this module operates at the platform level, it completely bypasses the standard `TenantContext` isolation filter, allowing a Super Admin to view, edit, suspend, and provision *any* tenant.

### 1. `SuperAdminTenantService`
This acts as the core engine for platform governance, providing:
- **`getPlatformStats`**: Natively aggregates data across `TenantEntity` and `TenantConfigurationEntity` to provide the Super Admin dashboard KPIs (MRR, active tenants, tier breakdown).
- **`listTenants`**: A paginated, searchable list of all tenants across the system.
- **`getTenantDetail`**: Returns the complete configuration bounds (features enabled, user limits) for a specific ID.
- **`createTenant` (Onboarding Engine)**:
  - Creates the root `TenantEntity`.
  - Parses the selected tier (`SMALL_BUSINESS`, `MEDIUM_BUSINESS`, `ENTERPRISE`) and injects the corresponding feature flags into `TenantConfigurationEntity`.
  - **Clones System Roles:** Executes direct `JdbcTemplate` queries to copy all system `permissions`, `roles`, and `role_permissions` from the base Demo Tenant template to the new tenant.
  - Provisions the new tenant's first active `ADMIN` user.
- **`updateTenantConfiguration`**: Changes limits and features dynamically.
- **`suspendTenant` / `activateTenant`**: Toggles the `is_active` flag, immediately halting or restoring access for a business.

### 2. `SuperAdminTenantController`
A RESTful API endpoint set mapped under `/api/v1/super-admin/tenants`. 
Every endpoint here is globally secured by `SecurityConfig` to require the `ROLE_SUPERADMIN` authority.

## Plan Tier Auto-Configuration
When a Super Admin creates a tenant, they simply specify the plan tier. The system automatically defaults to:
- **SMALL_BUSINESS**: 1 Location, 5 Users, Core POS features.
- **MEDIUM_BUSINESS**: 3 Locations, 15 Users, Core + PO + Returns + Tax Config.
- **ENTERPRISE**: 999 Locations, 999 Users, All features (including Stock Transfers).
These values can be overriden manually at any time via `updateTenantConfiguration`.
