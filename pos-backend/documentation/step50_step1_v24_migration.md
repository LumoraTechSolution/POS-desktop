# Step 50 — Step 1: V24 Database Migration (Super Admin & Tenant Configuration)

## File
`backend/src/main/resources/db/migration/V24__super_admin_and_tenant_config.sql`

## What Was Created

### New Tables

#### `super_admins`
Platform-level operator accounts. **NOT scoped by tenant.**
- Stores Lumora operator credentials separate from the tenant `users` table
- Has no `tenant_id` column — governs the platform, not a business
- Fields: `id`, `email`, `password_hash`, `first_name`, `last_name`, `is_active`, `last_login_at`, `created_at`, `updated_at`
- Index: `idx_super_admins_email`

#### `tenant_configurations`
SaaS subscription governance — one row per tenant.
- **`plan_tier`**: `SMALL_BUSINESS` | `MEDIUM_BUSINESS` | `ENTERPRISE`
- **`max_locations`**: Maximum number of branches a tenant can create
- **`max_users`**: Maximum number of users a tenant can have
- **`max_products`**: Maximum products in catalog
- **`features_enabled`**: JSONB array of feature tags the tenant can access
  - Example: `["SALES","INVENTORY","REPORTS","CUSTOMERS","EMPLOYEES"]`
- **`is_active`**: Suspension toggle — `FALSE` means the tenant is suspended and cannot log in
- **`subscription_start` / `subscription_end`**: Subscription validity window
- **`notes`**: Internal admin notes (only super admins see these)
- Indexes: `tenant_id`, `is_active`, `plan_tier`

## Seed Data
| Record | Value |
|--------|-------|
| Demo Tenant Config | `ENTERPRISE` plan, max 999 locations/users/999999 products, all features enabled |
| Default Super Admin | `superadmin@lumora.com` / `SuperAdmin@2024` |

## Feature Tags (Complete List)
```json
[
  "SALES", "INVENTORY", "REPORTS", "CUSTOMERS", "EMPLOYEES",
  "PURCHASE_ORDERS", "STOCK_TRANSFERS", "RETURNS", "TAX_CONFIG",
  "TIME_CLOCK", "ADVANCED_ANALYTICS", "API_ACCESS"
]
```

## Plan Tier Defaults
| Tier | Locations | Users | Products | Features |
|------|-----------|-------|----------|----------|
| SMALL_BUSINESS | 1 | 5 | 500 | SALES, INVENTORY, REPORTS, CUSTOMERS, EMPLOYEES |
| MEDIUM_BUSINESS | 3 | 15 | 5000 | + PURCHASE_ORDERS, RETURNS, TAX_CONFIG |
| ENTERPRISE | 999 | 999 | 999999 | All features |

## Security Note
The BCrypt hash in the seed uses cost=12, matching the application's `BCryptPasswordEncoder(12)`.
**Change the default super admin password immediately after first deployment.**
