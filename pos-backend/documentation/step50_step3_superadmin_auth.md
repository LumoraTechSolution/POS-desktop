# Step 50 — Step 3: Super Admin Authentication & Security

## Summary
To safely grant Super Admins access to the system without entangling them in tenant rules, we created a completely isolated authenticaton pathway.

### 1. `JwtTokenProvider`
Updated to generate and parse two types of JWTs:
- **Tenant Pass (USER):** Includes a `tenantId` claim. Scoped only to one business.
- **Master Pass (SUPERADMIN):** Has **no** `tenantId` claim, but contains `tokenType = "SUPERADMIN"` and `ROLE_SUPERADMIN`.

### 2. `JwtAuthenticationFilter`
The gatekeeper filter now forks based on the token type:
- If `USER`: Extract `tenantId`, set `TenantContext`, load data from `users` table via `CustomUserDetailsService`.
- If `SUPERADMIN`: Ignore `TenantContext`, load data from `super_admins` table via `SuperAdminUserDetailsService`.

### 3. `SecurityConfig`
Updated global security rules:
- `permitAll()` for `/api/v1/super-admin/auth/**` (login endpoint).
- `hasRole("SUPERADMIN")` for all other `/api/v1/super-admin/**` endpoints.

### 4. `SuperAdminAuthService` & Controller
Provides a dedicated login endpoint `POST /api/v1/super-admin/auth/login`.
- Queries `super_admins` directly (bypassing `AuthenticationManager`).
- Generates the "Master Pass" JWT upon success.
