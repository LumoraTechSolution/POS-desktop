# Step 50 — Step 5: Backend Interceptor (Feature Guard)

## Summary
To enforce tiered SaaS subscriptions, we implemented a custom `HandlerInterceptor` that acts as an "API Bouncer". Even if a tenant user has the correct `ROLE` (e.g., `MANAGER`), they cannot access an endpoint if their business's plan tier doesn't include the necessary feature.

### 1. `FeatureGuardInterceptor`
This interceptor runs globally on every `/api/v1/**` request *after* the `JwtAuthenticationFilter` (so the `TenantContext` is known).

**Logic Flow:**
1. Check if a `tenantId` is set (Super Admins skip this step).
2. Match the `requestURI` against a hardcoded map of feature-restricted routes.
3. If a match is found (e.g., `/api/v1/purchase-orders` requires `PURCHASE_ORDERS`), query the `TenantConfiguration`.
4. If the tenant's `features_enabled` JSONB array does not contain the required tag, block the request and return a structured `403 Forbidden` JSON response.

**Restricted Routes Managed:**
| Path Prefix | Required Feature |
|-------------|------------------|
| `/api/v1/purchase-orders` | `PURCHASE_ORDERS` |
| `/api/v1/stock-transfers` | `STOCK_TRANSFERS` |
| `/api/v1/returns` | `RETURNS` |
| `/api/v1/taxes` | `TAX_CONFIG` |
| `/api/v1/time-records` | `TIME_CLOCK` |
| `/api/v1/reports/profitability` | `ADVANCED_ANALYTICS` |
| `/api/v1/reports/inventory-valuation` | `ADVANCED_ANALYTICS` |

*Core operations like sales, reading dashboard KPIs, managing base inventory, and basic reporting are granted to all tenants implicitly.*

### 2. `WebMvcConfig`
A standard Spring `WebMvcConfigurer` that registers the `FeatureGuardInterceptor` with the application's interceptor registry, explicitly ignoring public auth endpoints to save overhead.

## Performance Note
For MVP scale, querying the `tenant_configurations` table by `tenantId` (which has a unique index) is extremely fast. However, as the system scales to thousands of tenants, this lookup is a primary candidate for in-memory caching (e.g., injecting a `Caffeine` cache or Redis for the `features_enabled` array per tenant).
