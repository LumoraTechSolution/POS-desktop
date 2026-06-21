# Step 50: Super Admin Control Panel — Implementation Plan

## 📌 Objective
Build a Super Admin governance layer that allows Lumora platform operators to centrally manage tenants, control subscription tiers, toggle features, and monitor platform-wide usage — all without touching the database directly.

---

## 🏗️ Implementation Steps

### Step 1: Database Migration (V24) — Tenant Configuration & Super Admin Tables
**Scope:** Create the `tenant_configurations` table and seed the `SUPERADMIN` role.
- **New Table: `tenant_configurations`**
  - `id` (UUID, PK)
  - `tenant_id` (UUID, FK → tenants, UNIQUE)
  - `plan_tier` (VARCHAR — `SMALL_BUSINESS`, `MEDIUM_BUSINESS`, `ENTERPRISE`)
  - `max_locations` (INTEGER, default 1)
  - `max_users` (INTEGER, default 3)
  - `max_products` (INTEGER, default 500)
  - `features_enabled` (JSONB — array of feature tags)
  - `is_active` (BOOLEAN, default TRUE — tenant suspension toggle)
  - `subscription_start` (TIMESTAMP)
  - `subscription_end` (TIMESTAMP, nullable)
  - `notes` (TEXT, nullable — internal admin notes)
  - `created_at`, `updated_at` (TIMESTAMP)
- **New Table: `super_admins`** (platform-level users, NOT scoped by tenant)
  - `id` (UUID, PK)
  - `email` (VARCHAR, UNIQUE)
  - `password_hash` (VARCHAR)
  - `first_name`, `last_name` (VARCHAR)
  - `is_active` (BOOLEAN)
  - `created_at`, `updated_at` (TIMESTAMP)
- **Seed:** Insert a default super admin user and default tenant configuration for Demo Business.

### Step 2: Backend — Super Admin Entity, Repository, DTO Layer
**Scope:** Create the JPA entities and data access layer.
- `SuperAdminEntity` (standalone entity, not extending `BaseEntity` — no tenant scoping)
- `TenantConfigurationEntity` (standalone entity, not extending `BaseEntity`)
- `PlanTier` enum (`SMALL_BUSINESS`, `MEDIUM_BUSINESS`, `ENTERPRISE`)
- `Feature` enum (`SALES`, `INVENTORY`, `REPORTS`, `CUSTOMERS`, `EMPLOYEES`, `PURCHASE_ORDERS`, `STOCK_TRANSFERS`, `RETURNS`, `TAX_CONFIG`, `TIME_CLOCK`, `ADVANCED_ANALYTICS`, `API_ACCESS`)
- DTOs: `TenantDetailResponse`, `TenantConfigurationRequest`, `TenantSummaryResponse`, `SuperAdminLoginRequest`, `SuperAdminAuthResponse`
- Repositories: `SuperAdminRepository`, `TenantConfigurationRepository`, `TenantRepository` (read access to `tenants` table)

### Step 3: Backend — Super Admin Authentication Service
**Scope:** Separate auth flow for super admins (no tenant context needed).
- `SuperAdminAuthService`: Login using email/password, generates JWT with `ROLE_SUPERADMIN` authority.
- Modify `JwtTokenProvider` to support optional `tenantId` (null for super admin tokens).
- Modify `JwtAuthenticationFilter` to detect super admin tokens and skip tenant context setting.
- New `SuperAdminAuthController` with endpoint `POST /api/v1/super-admin/auth/login`.

### Step 4: Backend — Super Admin Tenant Management Service & Controller
**Scope:** Full CRUD on tenant configurations + tenant overview API.
- `SuperAdminTenantService`:
  - `listAllTenants()` — paginated list with config summary
  - `getTenantDetail(tenantId)` — full tenant info + config + usage stats
  - `createTenant(request)` — provision new tenant with default config
  - `updateTenantConfiguration(tenantId, request)` — modify limits/features
  - `suspendTenant(tenantId)` / `activateTenant(tenantId)` — toggle `is_active`
  - `getPlatformStats()` — aggregate platform metrics (total tenants, active, revenue)
- `SuperAdminTenantController`:
  - `GET    /api/v1/super-admin/tenants` — list all tenants
  - `GET    /api/v1/super-admin/tenants/{id}` — tenant detail
  - `POST   /api/v1/super-admin/tenants` — create new tenant
  - `PUT    /api/v1/super-admin/tenants/{id}/config` — update configuration
  - `PATCH  /api/v1/super-admin/tenants/{id}/suspend` — suspend tenant
  - `PATCH  /api/v1/super-admin/tenants/{id}/activate` — activate tenant
  - `GET    /api/v1/super-admin/stats` — platform-wide statistics
- All endpoints secured with `@PreAuthorize("hasRole('SUPERADMIN')")`

### Step 5: Backend — Security Config Updates
**Scope:** Wire super admin security into the existing filter chain.
- Update `SecurityConfig` to permit `/api/v1/super-admin/auth/**` publicly.
- Update `JwtAuthenticationFilter` to handle super admin JWT (no tenant context).
- Add `SuperAdminUserDetailsService` for loading super admin by ID.

### Step 6: Frontend — Super Admin Auth & Layout
**Scope:** Create the dedicated super admin frontend area.
- New route group: `/(system-admin)` with its own layout
- `SuperAdminLoginPage`: Dedicated login page at `/system-admin/login`
- `superAdminAuthService.ts`: Auth service (login, token storage, logout)
- `SuperAdminLayout`: Sidebar with navigation (Dashboard, Tenants, Settings)
- Route guard: Redirect non-SUPERADMIN users

### Step 7: Frontend — Platform Dashboard Page
**Scope:** Build the main super admin overview.
- `/system-admin/dashboard`: Platform-wide KPI overview
  - Total active tenants
  - Total users across platform
  - Revenue metrics (MRR if tracked)
  - Tenants by plan tier (pie/bar chart)
  - Recent tenant activity feed

### Step 8: Frontend — Tenant Management Pages
**Scope:** Build tenant CRUD UI.
- `/system-admin/tenants`: Paginated tenant list with search/filter
  - Columns: Name, Domain, Plan, Status, Users, Locations, Created
  - Action buttons: View, Suspend/Activate
- `/system-admin/tenants/[id]`: Tenant detail page
  - Tab 1: Overview (tenant info, current usage vs limits)
  - Tab 2: Configuration (editable form for plan, limits, features)
  - Tab 3: Users (list of users for that tenant)
- `/system-admin/tenants/new`: New tenant creation form
- `superAdminService.ts`: Frontend service for all super admin API calls

### Step 9: Frontend — Feature Toggle & Tier Configuration UI
**Scope:** Build the configuration management interface.
- Feature checklist component with grouped toggles
- Plan tier dropdown with preset defaults
- Usage meters (Locations: 1/1, Users: 3/5, Products: 120/500)
- Confirmation modals for destructive actions (suspend, downgrade)

### Step 10: Documentation & Testing
**Scope:** Document everything and verify.
- Update backend and frontend documentation folders
- Test all endpoints manually
- Verify super admin login flow end-to-end
- Verify tenant CRUD operations
- Verify security (tenant users cannot access super admin routes)

---

## 📊 Technical Architecture Notes

### Super Admin vs Regular Auth
- Super admins exist in a **separate table** (`super_admins`), NOT in the `users` table
- Super admin JWTs have `tenantId: null` and `authorities: [ROLE_SUPERADMIN]`
- The `JwtAuthenticationFilter` will detect null tenantId and skip `TenantContext.setTenantId()`
- Super admin endpoints query across ALL tenants (no tenant filter applied)

### Feature Tags (JSON Array in `tenant_configurations.features_enabled`)
```json
["SALES", "INVENTORY", "REPORTS", "CUSTOMERS", "EMPLOYEES"]
```
Small Business gets core features. Medium adds `PURCHASE_ORDERS`, `STOCK_TRANSFERS`, `RETURNS`. Enterprise gets everything including `ADVANCED_ANALYTICS`, `API_ACCESS`.

### Plan Tier Defaults
| Tier | Locations | Users | Products | Features |
|------|-----------|-------|----------|----------|
| SMALL_BUSINESS | 1 | 5 | 500 | Core (5 modules) |
| MEDIUM_BUSINESS | 3 | 15 | 5000 | Core + 3 advanced |
| ENTERPRISE | Unlimited | Unlimited | Unlimited | All features |
