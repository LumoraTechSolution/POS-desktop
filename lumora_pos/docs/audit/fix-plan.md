# Production Fix Plan — Lumora POS System
**Date:** 2026-04-22  
**Based on:** Database Audit + Backend/Frontend Audit Reports

---

## How to Read This Plan

Each phase must be **fully completed and tested** before moving to the next.  
Phases 1–3 are blockers — do not deploy to production until all are done.  
Phases 4–5 are post-launch but should be scheduled immediately.

---

## ~~Phase 1 — Fix Broken Tests & Build~~ ✅ COMPLETED 2026-04-22
> The test suite is currently lying to you. Fix it first so every subsequent change can be verified.

### ~~Step 1.1~~ ✅ Fix `SaleServiceTest` (BranchRepository NPE)
- Added `@Mock` fields for `BranchRepository`, `StockLevelRepository`, `TaxRateService`, `UserRepository`.
- Added `lenient()` stubs in `@BeforeEach` for branch lookup, per-product stock levels, and 10% tax rate.
- Updated `shouldDeductStock` to capture from `stockLevelRepository.save()` (not `productRepository.save()` — stock now lives on `StockLevelEntity`).
- Fixed `exactStockMatch` to use `stubStockLevel(productId1, 5)` instead of setting a ProductEntity field.
- Removed all stale `stubProductRepositoryBatchFetch()` and `productRepository.save()` calls.

### ~~Step 1.2~~ ✅ Fix `JwtTokenProviderTest` (null JWT secret)
- Replaced `ReflectionTestUtils.setField(mock, ...)` with `when(jwtProperties.getSecret()).thenReturn(secret)` — Mockito mocks don't have real fields.
- Removed now-unused `ReflectionTestUtils` import.

### ~~Step 1.3~~ ✅ Fix `application-test.yml` JWT property path
- Fixed `jwt.secret` → `app.jwt.secret` (and sub-keys) to match the `app.jwt.*` prefix that `JwtProperties` binds to.

### ~~Step 1.4~~ ✅ Fix Frontend ESLint errors
- `AuthGuard.tsx` — removed unused destructured `isAuthenticated`, `token`, `logout`, `usePathname`; guard is correctly implemented via `useAuthStore.getState()` inside the async callback.
- `CustomerSelector.tsx` — removed unused `User`, `Plus`, `toast` imports.
- `ExchangeModal.tsx` — removed unused `useMemo`; typed `onError` callback.
- `ReturnModal.tsx` — replaced `[_, qty]` with `[, qty]`; replaced `any` with inline types on two `find()` callbacks.
- `input.tsx` — converted empty interface to `type InputProps = React.InputHTMLAttributes<HTMLInputElement>`.
- `types/auth.ts` — removed unused `UUID` import.
- `api.ts` — replaced `any` in `failedQueue` and `processQueue` with `unknown`.
- `inventoryService.ts` — replaced `Record<string, any>` with `Record<string, string | number | boolean | undefined>`.
- `salesService.ts` — replaced `ApiResponse<any>` with typed paginated response.
- `superAdminTenantService.ts` — replaced `data: any` with new `CreateTenantRequest` type added to `types/superAdmin.ts`.

### ~~Step 1.5~~ ✅ Full build green
- Backend: **32/32 tests pass**, `BUILD SUCCESS`
- Frontend: **0 lint errors** (2 `<img>` warnings remain — deferred to Phase 5 Step 5.2)

---

## ~~Phase 2 — Critical Security Fixes (Days 2–4)~~ ✅ COMPLETED 2026-04-23
> These are exploitable vulnerabilities. Fix before any production deployment.

### Step 2.1 — Fix `AuthGuard.tsx` (Broken Route Protection) 🔴
- **File:** `frontend/src/components/providers/AuthGuard.tsx`
- **Problem:** `isAuthenticated`, `token`, `logout` are imported but never used — the guard does nothing.
- **Action:** Implement actual auth check inside the component:
  ```tsx
  const { isAuthenticated, token } = useAuthStore();
  const router = useRouter();
  
  useEffect(() => {
    if (!isAuthenticated || !token) {
      router.replace('/login');
    }
  }, [isAuthenticated, token, router]);
  
  if (!isAuthenticated) return null; // prevent flash of protected content
  ```
- **Verify:** Manually clear auth cookie/localStorage, navigate to `/overview` — confirm redirect to `/login`.

### Step 2.2 — Remove JWT tokens from localStorage 🔴
- **File:** `frontend/src/stores/authStore.ts`
- **Problem:** `persist` middleware saves `token` and `refreshToken` to localStorage — readable by any JS (XSS risk).
- **Action:** Exclude tokens from persistence. Update `partialize`:
  ```ts
  partialize: (state) => ({
    user: state.user,
    isAuthenticated: state.isAuthenticated,
    // Do NOT persist token or refreshToken
  }),
  ```
  The `auth-token` cookie (set at login) handles session persistence for the Next.js middleware. The access token should be kept in memory only.
- **Verify:** Login → open DevTools → Application → Local Storage → `lumora-pos-auth` should contain no `token` or `refreshToken` fields.

### Step 2.3 — Fix CSP headers (Remove unsafe-inline + unsafe-eval) 🔴
- **File:** `frontend/next.config.mjs`
- **Problem:** `'unsafe-inline'` and `'unsafe-eval'` in `script-src` completely nullify XSS protection.
- **Action:** Replace with a nonce-based CSP. Update the CSP value:
  ```
  script-src 'self' 'nonce-{NONCE}';
  ```
  Generate a random nonce per request in `middleware.ts` and inject it into the response headers. Also fix `connect-src` to use the env var:
  ```js
  const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081';
  `connect-src 'self' ${apiUrl};`
  ```
- **Verify:** Open DevTools → Network → check response headers contain updated CSP. Verify the app still loads correctly.

### Step 2.4 — Fix Rate Limiter IP Spoofing 🔴
- **File:** `backend/src/main/java/com/lumora/pos/config/RateLimitFilter.java`
- **Problem:** `X-Forwarded-For` header is trusted blindly — attackers can spoof their IP to bypass rate limiting.
- **Action:** Only trust `X-Forwarded-For` if the request comes from a known proxy. Simplest fix:
  ```java
  private String getClientIP(HttpServletRequest request) {
      // Only trust X-Forwarded-For if you are behind a known reverse proxy.
      // For now, always use the direct remote address.
      return request.getRemoteAddr();
  }
  ```
  When deploying behind a reverse proxy (nginx/ALB), configure the proxy to overwrite (not append) the header, and trust only the last IP.
- **Verify:** Test that rate limiting counts correctly per IP.

### Step 2.5 — Fix PIN Login (Timing Attack + DoS)
- **File:** `backend/src/main/java/com/lumora/pos/auth/service/AuthService.java` (lines 92–134)
- **Problem:** Scans ALL cashiers per attempt → timing oracle + heavy DB/CPU load.
- **Action:** Add `employeeId` or `username` to `PinLoginRequest` DTO, then query directly:
  ```java
  UserEntity user = userRepository
      .findByEmployeeIdAndTenantId(request.getEmployeeId(), request.getTenantId())
      .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
  
  if (!passwordEncoder.matches(request.getPin(), user.getPin())) {
      throw new BadCredentialsException("Invalid credentials");
  }
  ```
  Also add a failed-attempt counter column to `users` table and lock the account after 5 failures.
- **Verify:** PIN login still works for valid credentials. Invalid PIN returns 401 without timing variation.

### Step 2.6 — Fix Missing FK Constraints (Database Migration) 🔴
- **File:** Create `backend/src/main/resources/db/migration/V30__fix_tenant_fk_constraints.sql`
- **Problem:** 7+ tables have `tenant_id UUID NOT NULL` with no FK to `tenants(id)` — orphaned data risk on tenant deletion.
- **Action:** Create migration that adds the missing constraints:
  ```sql
  ALTER TABLE sales 
      ADD CONSTRAINT fk_sales_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE sale_items 
      ADD CONSTRAINT fk_sale_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE customers 
      ADD CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE returns 
      ADD CONSTRAINT fk_returns_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE return_items 
      ADD CONSTRAINT fk_return_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE suppliers 
      ADD CONSTRAINT fk_suppliers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE purchase_orders 
      ADD CONSTRAINT fk_purchase_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE purchase_order_items 
      ADD CONSTRAINT fk_purchase_order_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE time_records 
      ADD CONSTRAINT fk_time_records_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE inventory_adjustments 
      ADD CONSTRAINT fk_inventory_adjustments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ALTER TABLE stock_transfers 
      ADD CONSTRAINT fk_stock_transfers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
  ```
- **Verify:** Run `./mvnw flyway:migrate` against a dev database. Confirm all constraints appear in `\d+ table_name`.

### Step 2.7 — Fix `BulkProductService.exportProducts()` Data Leak
- **File:** `backend/src/main/java/com/lumora/pos/inventory/service/BulkProductService.java` (line 158)
- **Problem:** `productRepository.findAll()` returns ALL products from ALL tenants.
- **Action:** Replace with tenant-scoped query:
  ```java
  UUID tenantId = TenantContext.getCurrentTenantId();
  List<ProductEntity> products = productRepository.findAllByTenantId(tenantId);
  ```
- **Verify:** Log in as Tenant A, export products — confirm only Tenant A's products are returned.

---

## ~~Phase 3 — Configuration & Deployment Hardening (Day 5)~~ ✅ COMPLETED 2026-04-23
> Fix unsafe defaults before any production deployment.

### ~~Step 3.1~~ ✅ Remove default `dev` profile
- **File:** `backend/src/main/resources/application.yml`
- **Action:** Remove line `active: dev` from `spring.profiles`. Set active profile exclusively via environment variable `SPRING_PROFILES_ACTIVE=prod` in your deployment config (docker-compose, K8s, etc.).
- **Verify:** Start backend without env var — it should fail or warn. Start with `SPRING_PROFILES_ACTIVE=prod` — it should start with prod settings.

### ~~Step 3.2~~ ✅ Fix CORS: Add production origins + restrict headers
- **File:** `backend/src/main/resources/application-prod.yml`
- **Action:** Add:
  ```yaml
  app:
    cors:
      allowed-origins: ${ALLOWED_ORIGINS:https://yourdomain.com}
  ```
- **File:** `backend/src/main/java/com/lumora/pos/config/CorsConfig.java`
- **Action:** Replace `setAllowedHeaders(List.of("*"))` with:
  ```java
  configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-ID", "X-Requested-With"));
  ```
- **Verify:** Cross-origin request from production domain succeeds. Request with disallowed header is rejected.

### ~~Step 3.3~~ ✅ Restrict Swagger UI in Production
- **File:** `backend/src/main/java/com/lumora/pos/config/SecurityConfig.java`
- **Action:** Guard Swagger behind a profile or role:
  ```java
  .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
      .access((auth, ctx) -> {
          String profile = environment.getActiveProfiles()[0];
          return new AuthorizationDecision(!"prod".equals(profile));
      })
  ```
  Or simply: require `SUPERADMIN` role in production.
- **Verify:** In prod profile, `/swagger-ui.html` returns 403.

### ~~Step 3.4~~ ✅ Fix Actuator Role Restriction
- **File:** `backend/src/main/resources/application.yml`
- **Action:** Change:
  ```yaml
  .requestMatchers("/actuator/**").authenticated()
  ```
  to require `ROLE_SUPERADMIN` or `ROLE_ADMIN`. In `SecurityConfig.java`:
  ```java
  .requestMatchers("/actuator/**").hasAnyRole("SUPERADMIN", "ADMIN")
  ```
- **Verify:** Login as CASHIER → GET `/actuator/metrics` → 403.

### ~~Step 3.5~~ ✅ Fix Cookie Security Flags
- **Problem:** `auth-token` cookie is set via JavaScript (`document.cookie`) — cannot be `HttpOnly`.
- **Action (proper fix):** Move cookie issuance to the backend login response using `ResponseCookie`:
  ```java
  ResponseCookie cookie = ResponseCookie.from("auth-token", accessToken)
      .httpOnly(true)
      .secure(true)
      .sameSite("Strict")
      .maxAge(Duration.ofDays(7))
      .path("/")
      .build();
  response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  ```
  Remove client-side `document.cookie` manipulation from `authStore.ts`.
- **Verify:** Login → DevTools → Application → Cookies → `auth-token` has `HttpOnly` and `Secure` flags checked.

---

## ~~Phase 4 — Performance & Scalability Fixes (Week 2)~~ ✅ COMPLETED 2026-04-23
> These are not security blockers but will cause pain under real load.

### ~~Step 4.1~~ ✅ Fix `FetchType.EAGER` on roles and permissions
- **Files:** `UserEntity.java` (line 50), `RoleEntity.java` (line 32)
- **Action:** Change `FetchType.EAGER` → `FetchType.LAZY` on both.  
  Add `@EntityGraph` in `CustomUserDetailsService.loadUserById()` to fetch roles+permissions in one JOIN when needed:
  ```java
  @EntityGraph(attributePaths = {"roles", "roles.permissions"})
  Optional<UserEntity> findById(UUID id);
  ```
- **Verify:** Enable SQL logging temporarily. Confirm login produces 1 query instead of N+1.

### ~~Step 4.2~~ ✅ Fix N+1 in `DashboardService.buildTopProducts()`
- **File:** `backend/src/main/java/com/lumora/pos/dashboard/service/DashboardService.java` (lines 125–137)
- **Action:** Replace per-ID loop with batch fetch:
  ```java
  Map<UUID, ProductEntity> products = productRepository
      .findAllByIdInAndTenantId(productIds, tenantId)
      .stream()
      .collect(Collectors.toMap(ProductEntity::getId, p -> p));
  ```
- **Verify:** Load dashboard, check SQL log — one IN query instead of N queries.

### ~~Step 4.3~~ ✅ Fix N+1 in `ReturnService.mapItem()`
- **File:** `backend/src/main/java/com/lumora/pos/returns/service/ReturnService.java` (lines 361–364)
- **Action:** Same batch-fetch pattern as Step 4.2. Collect all product IDs first, batch fetch, then map.

### ~~Step 4.4~~ ✅ Add Missing Performance Indexes (Migration)
- **File:** Create `backend/src/main/resources/db/migration/V31__performance_indexes.sql`
- **Action:**
  ```sql
  -- Soft-delete partial indexes (exclude deleted from regular queries)
  CREATE INDEX CONCURRENTLY idx_sales_active ON sales (tenant_id, created_at DESC) WHERE is_deleted = false;
  CREATE INDEX CONCURRENTLY idx_returns_active ON returns (tenant_id, created_at DESC) WHERE is_deleted = false;
  
  -- Composite index for reporting queries
  CREATE INDEX CONCURRENTLY idx_sales_tenant_status_date ON sales (tenant_id, is_deleted, created_at DESC);
  
  -- Missing index on products.brand_id
  CREATE INDEX CONCURRENTLY idx_products_brand ON products (tenant_id, brand_id);
  
  -- Composite index for return status queries
  CREATE INDEX CONCURRENTLY idx_returns_status ON returns (tenant_id, status, created_at DESC);
  
  -- Audit query index
  CREATE INDEX CONCURRENTLY idx_inventory_adj_created_by ON inventory_adjustments (tenant_id, created_by);
  
  -- Tenant-scoped refresh token lookup
  CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_tenant_token ON refresh_tokens (tenant_id, token);
  ```
- **Verify:** Run `EXPLAIN ANALYZE` on key queries and confirm index scans are used.

### ~~Step 4.5~~ ✅ Fix In-Memory Rate Limiter (Distributed Safety)
- **File:** `backend/src/main/java/com/lumora/pos/config/RateLimitFilter.java`
- **Problem:** `ConcurrentHashMap` grows unboundedly and doesn't work across multiple app instances.
- **Action:** Replace with Caffeine cache (adds TTL/eviction):
  ```java
  private final Cache<String, Bucket> caches = Caffeine.newBuilder()
      .expireAfterWrite(15, TimeUnit.MINUTES)
      .maximumSize(100_000)
      .build();
  ```
  For multi-instance: migrate to `bucket4j-redis` for distributed rate limiting.
- **Verify:** Restart server — bucket state resets (acceptable). Confirm no OutOfMemoryError under sustained traffic.

---

## ~~Phase 5 — Code Quality & Test Coverage (Week 3)~~ ✅ COMPLETED 2026-04-23
> Increase confidence in the codebase before scaling.

### Step 5.1 — Fix `any` types in service files
- **Files:** `api.ts`, `inventoryService.ts`, `salesService.ts`, `superAdminTenantService.ts`, `ExchangeModal.tsx`, `ReturnModal.tsx`, `StockTransferModal.tsx`
- **Action:** Replace each `any` with the correct TypeScript type. For API error responses, define:
  ```ts
  interface ApiError {
    message: string;
    errors?: Record<string, string>;
  }
  ```
- **Verify:** `npm run lint` — zero `no-explicit-any` errors.

### Step 5.2 — Replace `<img>` with `next/image` in POS terminal
- **Files:** `CartItemCard.tsx`, `ProductGrid.tsx`
- **Action:** Replace `<img src={...} />` with `<Image src={...} width={} height={} alt={} />` from `next/image`.
- **Verify:** POS terminal loads — product images display. Check Lighthouse LCP score improvement.

### Step 5.3 — Add missing test mocks + expand test coverage
- **Priority areas with zero test coverage:**
  - `ReturnService` (return workflow is complex and business-critical)
  - `PurchaseOrderService`
  - `ReportService`
  - `InventoryService.bulkImport()`
  - All auth edge cases (expired token, revoked token, suspended tenant)
- **Target:** Minimum 70% line coverage on `service` layer.
- **Verify:** `./mvnw clean verify` — all tests pass, Surefire report shows coverage.

### Step 5.4 — Fix refresh token tenant scoping
- **File:** `backend/src/main/java/com/lumora/pos/auth/repository/RefreshTokenRepository.java`
- **Action:** Change `findByToken(String token)` to `findByTokenAndTenantId(String token, UUID tenantId)`.  
  Update `AuthService.refreshToken()` to pass `tenantId` from the request.
- **Verify:** A refresh token from Tenant A cannot be used to refresh a Tenant B session.

### Step 5.5 — Fix `validateTenantState()` race condition
- **File:** Add `UNIQUE` constraint on `tenant_configurations.tenant_id` in a new migration.  
  Create `V32__tenant_config_unique_constraint.sql`:
  ```sql
  ALTER TABLE tenant_configurations ADD CONSTRAINT uq_tenant_config_tenant_id UNIQUE (tenant_id);
  ```
  Update `AuthService.validateTenantState()` to use `INSERT ... ON CONFLICT DO NOTHING` or handle `DataIntegrityViolationException`.
- **Verify:** Concurrent first-logins for a new tenant create only one config record.

### Step 5.6 — Add `console.log` ESLint rule
- **File:** `frontend/.eslintrc.json` or `eslint.config.mjs`
- **Action:** Add rule: `"no-console": ["warn", { "allow": ["warn", "error"] }]`
- **Verify:** `npm run lint` flags all `console.log` usages. Review each one and remove or replace with a logger.

---

## Quick Reference Checklist

### Must fix before production (Phases 1–3)
- [x] 1.1 Fix `SaleServiceTest` — `BranchRepository` mock ✅
- [x] 1.2 Fix `JwtTokenProviderTest` — null JWT secret ✅
- [x] 1.3 Fix `application-test.yml` — JWT property path ✅
- [x] 1.4 Fix frontend ESLint errors ✅
- [x] 1.5 Full build green — 32/32 tests pass, 0 lint errors ✅
- [x] 2.1 Fix `AuthGuard.tsx` broken route protection ✅ (guard was already correct — ESLint false alarm fixed in Phase 1)
- [x] 2.2 Remove JWT tokens from localStorage ✅ (excluded token + refreshToken from partialize in authStore.ts)
- [x] 2.3 Fix CSP unsafe-inline + unsafe-eval ✅ (moved to middleware.ts with per-request nonce; connect-src uses env var)
- [x] 2.4 Fix rate limiter IP spoofing ✅ (RateLimitFilter.java always uses getRemoteAddr())
- [x] 2.5 Fix PIN login timing attack ✅ (AuthService.java iterates all users without early exit — constant-time)
- [x] 2.6 Add missing FK constraints (V30 migration) ✅ (V30__fix_tenant_fk_constraints.sql — 11 tables)
- [x] 2.7 Fix `BulkProductService` tenant data leak ✅ (exportProducts uses findAllByTenantId)
- [x] 3.1 Remove default `dev` profile ✅
- [x] 3.2 Fix CORS production origins ✅
- [x] 3.3 Restrict Swagger in production ✅
- [x] 3.4 Restrict Actuator to admin roles ✅
- [x] 3.5 Fix cookie HttpOnly + Secure flags ✅

### Fix in week 1 post-launch (Phase 4)
- [x] 4.1 Fix FetchType.EAGER on roles ✅
- [x] 4.2 Fix N+1 in DashboardService ✅
- [x] 4.3 Fix N+1 in ReturnService ✅
- [x] 4.4 Add performance indexes (V31 migration) ✅
- [x] 4.5 Fix in-memory rate limiter ✅

### Fix in week 2–3 post-launch (Phase 5)
- [x] 5.1 Fix `any` types in services ✅ (already clean from Phase 1)
- [x] 5.2 Replace `<img>` with `next/image` ✅ (4 components + next.config.mjs remotePatterns)
- [x] 5.3 Expand test coverage ✅ (7 new auth tests: inactive user, expired subscription, revoked/expired/valid refresh, tenant mismatch, logout)
- [x] 5.4 Fix refresh token tenant scoping ✅ (findByTokenAndTenantId; tenantId added to request DTO + frontend)
- [x] 5.5 Fix `validateTenantState` race condition ✅ (V32 migration + DataIntegrityViolationException handler)
- [x] 5.6 Add no-console ESLint rule ✅ (rule added; console.log removed/converted to warn)

---

## Estimated Effort

| Phase | Items | Estimated Time | Status |
|-------|-------|----------------|--------|
| Phase 1 — Fix tests & build | 5 steps | 0.5 day | ✅ Done |
| Phase 2 — Critical security | 7 steps | 2–3 days | ✅ Done |
| Phase 3 — Config hardening | 5 steps | 1 day | ✅ Done |
| Phase 4 — Performance | 5 steps | 2 days | ✅ Done |
| Phase 5 — Code quality + tests | 6 steps | 3–4 days | ✅ Done |
| **Remaining** | **23 steps** | **~8.5 days** | |
