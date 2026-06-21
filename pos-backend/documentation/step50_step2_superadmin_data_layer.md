# Step 50 — Step 2: Backend Entity, Enum, DTO & Repository Layer

## Package: `com.lumora.pos.superadmin`

All files live in the new `superadmin` module:

```
com.lumora.pos.superadmin/
├── enums/
│   ├── PlanTier.java         — Subscription tier enum
│   └── Feature.java          — Feature flag enum
├── entity/
│   ├── SuperAdminEntity.java         — Platform-level operator (no tenant scope)
│   ├── TenantConfigurationEntity.java — Subscription config per tenant
│   └── TenantEntity.java             — JPA mapping for existing `tenants` table
├── repository/
│   ├── SuperAdminRepository.java
│   ├── TenantConfigurationRepository.java
│   └── TenantRepository.java
└── dto/
    ├── SuperAdminLoginRequest.java    — Login form
    ├── SuperAdminAuthResponse.java    — Login response with JWT
    ├── TenantSummaryResponse.java     — Row in tenant list
    ├── TenantDetailResponse.java      — Full tenant detail page data
    ├── TenantConfigurationRequest.java — Update config form
    ├── CreateTenantRequest.java       — New tenant onboarding form
    └── PlatformStatsResponse.java     — Dashboard KPI response
```

## Key Design Decisions

### Entities NOT Extending BaseEntity
Both `SuperAdminEntity` and `TenantConfigurationEntity` intentionally do NOT extend `BaseEntity`:
- `BaseEntity` has a mandatory `tenant_id` column — super admin entities are platform-level
- `SuperAdminEntity` has no tenant scope at all
- `TenantConfigurationEntity.tenantId` is a reference TO a tenant, not a scope filter

### JSONB → List<String> Mapping
Uses Hibernate's native `@JdbcTypeCode(SqlTypes.JSON)` — exactly the same pattern as `AuditLogEntity`. No extra dependencies required.

### TenantEntity
A new JPA mapping for the existing `tenants` table from V1. This gives the Super Admin module typed access to query all tenants without raw SQL.

## Enums

### PlanTier
| Value | Market | Limits |
|-------|--------|--------|
| `SMALL_BUSINESS` | Single-store, cafes | 1 loc, 5 users, 500 products |
| `MEDIUM_BUSINESS` | Growing chains | 3 loc, 15 users, 5000 products |
| `ENTERPRISE` | Large franchises | 999 loc, 999 users, unlimited products |

### Feature (12 flags)
| Category | Flags |
|----------|-------|
| Core (all plans) | `SALES`, `INVENTORY`, `REPORTS`, `CUSTOMERS`, `EMPLOYEES` |
| Medium | `PURCHASE_ORDERS`, `RETURNS`, `TAX_CONFIG`, `TIME_CLOCK` |
| Enterprise | `STOCK_TRANSFERS`, `ADVANCED_ANALYTICS`, `API_ACCESS` |
