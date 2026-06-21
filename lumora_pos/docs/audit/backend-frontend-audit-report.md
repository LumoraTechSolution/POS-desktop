# Backend & Frontend Audit Report
**Project:** Lumora POS System  
**Date:** 2026-04-22  
**Auditor:** Claude Code  
**Scope:** Security, hidden errors, performance, scalability, production readiness

---

## Overall Production Readiness Verdict

| Layer | Status |
|-------|--------|
| Backend Security | ⚠️ NOT READY — 3 critical issues |
| Frontend Security | ⚠️ NOT READY — 2 critical issues |
| Configuration | ⚠️ NOT READY — unsafe defaults |
| Performance | ⚠️ NEEDS WORK — N+1 and in-memory caching |
| Error Handling | ✅ READY |
| Auth Flow | ✅ MOSTLY SOLID — with noted fixes |

---

## CRITICAL Findings

### BC-1 — JWT Access Token NOT Invalidated on Logout
- **File:** `backend/src/main/java/com/lumora/pos/auth/service/AuthService.java` (line 186–191)
- **Issue:** `logout()` only revokes refresh tokens. The access token remains valid for its full 24-hour lifespan after logout. A stolen token stays valid for up to 24 hours.
- **Fix:** Implement a token blocklist (Redis or DB table) checked in `JwtAuthenticationFilter.validateToken()`. Alternatively, reduce access token expiry to 15 minutes.

### BC-2 — PIN Login Timing Oracle + No Per-User Lockout
- **File:** `backend/src/main/java/com/lumora/pos/auth/service/AuthService.java` (lines 100–115)
- **Issue:** PIN auth iterates ALL active users in a tenant and calls `passwordEncoder.matches()` on each until a match is found. This means:
  1. Response time varies with the position of the matching user (timing attack).
  2. Rate limiting is IP-based only — distributed brute-force across IPs bypasses it.
  3. For a tenant with 100 cashiers, every PIN attempt triggers up to 100 BCrypt comparisons (BCrypt at factor 12 ≈ 250ms each → up to 25 seconds per request, DoS risk).
- **Fix:** Enforce PIN login by `employeeId` or `username` (not a tenant-wide scan). Add failed-attempt lockout per user in the DB.

### BC-3 — Rate Limiter IP Spoofing via X-Forwarded-For
- **File:** `backend/src/main/java/com/lumora/pos/config/RateLimitFilter.java` (lines 59–65)
- **Issue:** The `getClientIP()` method trusts `X-Forwarded-For` header. The check `!xfHeader.contains(request.getRemoteAddr())` is bypassable: an attacker can send `X-Forwarded-For: fake-ip, real-ip`. Since `xfHeader.contains(remoteAddr)` returns true, the code splits and uses `fake-ip`. Rate limiting can be fully bypassed.
- **Fix:** Only trust `X-Forwarded-For` if the request comes from a known reverse proxy IP. Otherwise use `request.getRemoteAddr()` only.

### FC-1 — JWT Tokens Stored in localStorage (XSS-Accessible)
- **File:** `frontend/src/stores/authStore.ts` (lines 33–81)
- **Issue:** Zustand `persist` middleware stores `token` and `refreshToken` in `localStorage` (default storage). Any XSS vulnerability in any dependency can read and exfiltrate both tokens.
- **Fix:** Store tokens in memory only (`partialize` should exclude `token` and `refreshToken`). Rely on the httpOnly cookie set by the backend for session persistence. Or move token issuance to Set-Cookie httpOnly server response.

### FC-2 — CSP `unsafe-inline` + `unsafe-eval` Negates XSS Protection
- **File:** `frontend/next.config.mjs` (line 9)
- **Issue:** `script-src 'self' 'unsafe-inline' 'unsafe-eval'` — these two directives completely disable CSP's XSS protection. Any injected script (from a compromised npm package, stored XSS, etc.) will execute freely.
- **Fix:** Remove `'unsafe-eval'`. Replace `'unsafe-inline'` with a nonce-based CSP (`'nonce-{random}'`) using Next.js middleware. This requires moving inline scripts to nonce-tagged blocks.

---

## HIGH Findings

### BH-1 — Rate Limiter is In-Memory (Not Cluster-Safe, Memory Leak)
- **File:** `backend/src/main/java/com/lumora/pos/config/RateLimitFilter.java` (line 27)
- **Issue:** `ConcurrentHashMap<String, Bucket>` stores rate limit state in-memory. In a multi-instance deployment (load balancer), each pod has independent counters — attacker gets 10 attempts per pod, not 10 total. The map also grows unboundedly (no TTL/eviction = memory leak over time).
- **Fix:** Use Bucket4j with Redis backend (`bucket4j-redis`) for distributed rate limiting. Add a Caffeine cache with TTL for the in-memory map as a minimum fix.

### BH-2 — Swagger UI Exposed in Production (No Auth)
- **File:** `backend/src/main/java/com/lumora/pos/config/SecurityConfig.java` (line 45)
- **Issue:** `permitAll()` on `/api-docs/**` and `/swagger-ui/**` with no profile guard. In production, this exposes the full API contract to unauthenticated users.
- **Fix:** Add `@Profile("!prod")` to the Swagger `permitAll` matcher, or require authentication in prod.

### BH-3 — Default Active Profile is `dev`
- **File:** `backend/src/main/resources/application.yml` (line 6)
- **Issue:** `spring.profiles.active: dev` — if a production deployment doesn't explicitly set `SPRING_PROFILES_ACTIVE=prod`, the `dev` profile activates. `dev` has `show-sql: true` (logs all SQL queries with data) and potentially weaker settings.
- **Fix:** Remove `spring.profiles.active` from `application.yml`. Set it exclusively via env var in deployment.

### BH-4 — CORS Allows All Headers (`*`)
- **File:** `backend/src/main/java/com/lumora/pos/config/CorsConfig.java` (line 24)
- **Issue:** `setAllowedHeaders(List.of("*"))` permits any custom header in cross-origin requests. This can expose internal headers unintentionally.
- **Fix:** Explicitly list required headers: `Authorization`, `Content-Type`, `X-Tenant-ID`, `X-Requested-With`.

### FH-1 — Cookie Missing `Secure` and `HttpOnly` Flags
- **File:** `frontend/src/stores/authStore.ts` (line 45)
- **Issue:** Cookie set as `auth-token=${token}; path=/; max-age=604800; samesite=lax` — missing `Secure` (sent over HTTP in non-HTTPS environments) and `HttpOnly` cannot be set via `document.cookie` (requires server-side `Set-Cookie` header). The cookie is fully readable by JavaScript.
- **Fix:** Move auth cookie issuance to the backend via `Set-Cookie: auth-token=...; HttpOnly; Secure; SameSite=Strict`. Remove client-side cookie manipulation.

### FH-2 — CSP `connect-src` Hardcodes `localhost` 
- **File:** `frontend/next.config.mjs` (line 13)
- **Issue:** `connect-src 'self' http://localhost:8081 http://localhost:3000` — production builds ship CSP headers that allow connections to `localhost:8081`. This is a misconfiguration and should use the production API URL from env vars.
- **Fix:** Build CSP dynamically using `NEXT_PUBLIC_API_URL` env var.

### FH-3 — Middleware Validates Cookie Presence Only (Not Token Validity)
- **File:** `frontend/src/middleware.ts` (lines 15–20)
- **Issue:** Route protection checks if `auth-token` cookie exists, not if the token is valid or unexpired. An expired token cookie passes the middleware and only fails at the first API call.
- **Fix:** Add JWT expiry check in middleware using `jose` library (edge-compatible JWT verification).

### FH-4 — `callbackUrl` Open Redirect
- **File:** `frontend/src/middleware.ts` (line 24)
- **Issue:** `url.searchParams.set('callbackUrl', pathname)` — if a user is tricked to visit `/login?callbackUrl=https://evil.com`, the login page could redirect to an external domain after auth.
- **Fix:** Validate `callbackUrl` is a relative path starting with `/` before redirecting.

---

## MEDIUM Findings

### BM-1 — `console.log` / Debug Logging in 9 Frontend Files
- **Files:** `error.tsx`, `receiptPrinterService.ts`, `hardwareService.ts`, `POSHeader.tsx`, multiple super-admin pages, `CustomerSelector.tsx`
- **Issue:** Production builds include `console.log` statements. POS and super-admin code may log sensitive operational data (customer lookups, tenant data).
- **Fix:** Use a proper logging utility that strips logs in production builds, or add `eslint-plugin-no-console` rule.

### BM-2 — PIN Login Loads All Users Per Attempt (Performance)
- **File:** `backend/src/main/java/com/lumora/pos/auth/service/AuthService.java` (line 100)
- **Issue:** `findActiveUsersWithPinByTenantId()` loads all cashiers into memory for every PIN attempt. At scale (50+ cashiers), this is a heavy query per login.
- **Fix:** Require `employeeId` in PIN login request and query `WHERE tenant_id = ? AND employee_id = ?`.

### BM-3 — `refreshToken()` Missing Tenant Validation
- **File:** `backend/src/main/java/com/lumora/pos/auth/service/AuthService.java` (line 137–183)
- **Issue:** `refreshTokenRepository.findByToken(request.getRefreshToken())` searches by token alone with no tenant scoping (matches DB audit finding H-2). A token from tenant A can theoretically be replayed to get tenant B credentials if IDs align.
- **Fix:** Add tenant scope: `findByTokenAndTenantId(token, tenantId)` where `tenantId` comes from the request body or existing access token.

### BM-4 — `validateTenantState()` Creates Config on First Login (Race Condition)
- **File:** `backend/src/main/java/com/lumora/pos/auth/service/AuthService.java` (lines 193–214)
- **Issue:** `orElseGet()` creates a new `TenantConfigurationEntity` if none exists. Under concurrent first logins for a new tenant, multiple threads could create duplicate configs (no unique constraint defense). The auto-provisioning also silently gives new tenants `SMALL_BUSINESS` plan without any validation.
- **Fix:** Add `UNIQUE` constraint on `tenant_configurations.tenant_id`. Separate provisioning from the login path.

### BM-5 — `application.yml` CORS `allowed-origins` Only `localhost:3000`
- **File:** `backend/src/main/resources/application.yml` (line 47)
- **Issue:** Base config sets `allowed-origins: http://localhost:3000`. The `application-prod.yml` does not override this. In production, cross-origin requests from the real domain will be blocked.
- **Fix:** Add `app.cors.allowed-origins: ${ALLOWED_ORIGINS}` in `application-prod.yml`.

---

## LOW Findings

### BL-1 — `show-sql: true` in Base Config
- **File:** `backend/src/main/resources/application.yml` (line 11)
- **Issue:** SQL queries logged by default. Even though `application-prod.yml` sets `show-sql: false`, if `dev` profile activates in prod (see BH-3), SQL with data is logged.

### BL-2 — BCryptPasswordEncoder Work Factor 12 (Acceptable, Monitor)
- **File:** `backend/src/main/java/com/lumora/pos/config/SecurityConfig.java` (line 61)
- **Issue:** Factor 12 is good but at scale, high-volume PIN logins (all-users-scan pattern) multiply this cost. Monitor login latency under load.

### BL-3 — Actuator Metrics Exposed Without Role Check
- **File:** `backend/src/main/resources/application.yml` (lines 55–62)
- **Issue:** `/actuator/metrics` exposed with only `authenticated()` — any logged-in user (including CASHIER role) can access JVM metrics, heap stats, and DB connection pool info. Should be restricted to `ADMIN` or `SUPERADMIN`.

### FL-1 — `superAdminStore` Has No Session Recovery
- **File:** `frontend/src/stores/superAdminStore.ts`
- **Issue:** Super admin state is not persisted. Page refresh logs out the super admin silently. UX issue, not a security problem.

---

## What's Working Well ✅

| Area | Status |
|------|--------|
| JWT signing (HS256 with JJWT 0.12.x) | Correct — `verifyWith()` enforces signature |
| `alg:none` attack | Not possible with current JJWT version |
| Stack trace exposure | GlobalExceptionHandler returns generic messages |
| Password hashing | BCrypt factor 12 — strong |
| TenantContext cleanup | `finally { TenantContext.clear() }` present in filter |
| CSRF | Disabled correctly for stateless JWT API |
| Session management | `STATELESS` — correct |
| Flyway clean-disabled | `clean-disabled: true` in prod — correct |
| `ddl-auto: validate` | Correct — no auto-schema modification |
| `open-in-view: false` | Correct — no lazy loading outside transactions |
| `X-Frame-Options: DENY` | Set correctly |
| `X-Content-Type-Options: nosniff` | Set correctly |
| Route protection (middleware) | All non-public routes guarded |
| Soft delete pattern | Consistent across entities |
| Optimistic locking | `@Version` on transactional entities |

---

## Prioritized Fix List

### Block Production (Fix Before Deploy)

| # | Finding | File | Effort |
|---|---------|------|--------|
| 1 | Tokens in localStorage (FC-1) | `authStore.ts` | Medium |
| 2 | CSP unsafe-inline/eval (FC-2) | `next.config.mjs` | Medium |
| 3 | Rate limiter IP spoofing (BC-3) | `RateLimitFilter.java` | Low |
| 4 | PIN login timing + DoS (BC-2) | `AuthService.java` | Medium |
| 5 | CORS allowed-origins missing in prod (BM-5) | `application-prod.yml` | Low |
| 6 | Default profile is dev (BH-3) | `application.yml` | Low |
| 7 | Swagger open in prod (BH-2) | `SecurityConfig.java` | Low |

### High Priority (Week 1 Post-Deploy)

| # | Finding | File | Effort |
|---|---------|------|--------|
| 8 | Access token not invalidated on logout (BC-1) | `AuthService.java` + blocklist | High |
| 9 | Cookie missing Secure + HttpOnly (FH-1) | Backend auth endpoint | Medium |
| 10 | CSP localhost in production (FH-2) | `next.config.mjs` | Low |
| 11 | Middleware doesn't validate token expiry (FH-3) | `middleware.ts` | Low |
| 12 | callbackUrl open redirect (FH-4) | `middleware.ts` | Low |
| 13 | Rate limiter in-memory only (BH-1) | `RateLimitFilter.java` | High |

### Medium Priority (Sprint 2)

| # | Finding | File | Effort |
|---|---------|------|--------|
| 14 | Refresh token missing tenant scope (BM-3) | `AuthService.java` | Low |
| 15 | validateTenantState race condition (BM-4) | DB migration + service | Low |
| 16 | Actuator unrestricted to all roles (BL-3) | `SecurityConfig.java` | Low |
| 17 | console.log in 9 frontend files (BM-1) | Multiple files | Low |
| 18 | CORS allows all headers (BH-4) | `CorsConfig.java` | Low |

---

## Build & Test Results

### Frontend Build — `npm run build`
**Result: ✅ Compiled successfully but with ESLint ERRORS**

The build completes, but ESLint errors are present that indicate real code quality and security issues:

#### CRITICAL — `AuthGuard.tsx` Does Not Guard Anything
- **File:** `frontend/src/components/providers/AuthGuard.tsx`
- **Issue:** ESLint reports `isAuthenticated`, `token`, and `logout` are all **assigned but never used**. This means `AuthGuard` imports auth state but never acts on it — the component likely provides **zero route protection**. Any user can access all routes wrapped by `AuthGuard` regardless of auth state.
- **Severity: CRITICAL** — this is a broken security control.

#### TypeScript `any` Usage in Services (Type Safety Gaps)
Files with `no-explicit-any` violations — these disable TypeScript's type checking at API boundaries, hiding potential runtime errors:
- `frontend/src/services/api.ts` (lines 41, 43)
- `frontend/src/services/inventoryService.ts` (line 52)
- `frontend/src/services/salesService.ts` (line 62)
- `frontend/src/services/superAdminTenantService.ts` (line 42)
- `frontend/src/components/pos/StockTransferModal.tsx` (line 41)
- `frontend/src/components/pos/ExchangeModal.tsx` (lines 45, 72, 84)
- `frontend/src/components/pos/ReturnModal.tsx` (lines 72, 84)

#### Unused Imports (Dead Code)
- `CustomerSelector.tsx` — `User`, `Plus`, `toast` imported but unused
- `ExchangeModal.tsx` — `useMemo` unused
- `ReturnModal.tsx` — `_` unused
- `types/auth.ts` — `UUID` imported but never used

#### Image Optimization Missing
- `CartItemCard.tsx` and `ProductGrid.tsx` use `<img>` instead of `next/image`. Product images in the POS terminal will load slowly and increase bandwidth usage — important for LCP performance.

---

### Backend Tests — `./mvnw clean verify`
**Result: ❌ FAILING — 16 errors, 3 failures out of 32 tests**

#### CRITICAL — `SaleServiceTest` (14 failures): `BranchRepository` not mocked
- **Root cause:** `BranchRepository` was added to `SaleService.createSale()` (line 63) but the unit test mock setup was never updated. `this.branchRepository` is `null` in ALL `SaleServiceTest` variants.
- **Affected tests:** All `SingleItemSales`, `MultiItemSales`, `StockManagement`, `PaymentInfo`, `AuditLogging`, `EdgeCases` nested classes.
- **Fix:** Add `@Mock private BranchRepository branchRepository;` and `when(branchRepository.findByIsDefaultTrueAndTenantId(...)).thenReturn(Optional.of(mockBranch));` in `SaleServiceTest` setup.

#### HIGH — `JwtTokenProviderTest` (3 failures): `JwtProperties` returns null secret
- **Root cause:** `JwtProperties` bean is not properly wired in the JWT test context. `getSecret()` returns `null`, causing NPE when building the signing key.
- **Fix:** Add `@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-long-enough-for-hs256")` to `JwtTokenProviderTest`.

#### HIGH — `PosApplicationTests.contextLoads` + `SaleServiceIntegrationTest`: Application context fails to load
- **Root cause:** Spring context fails to bootstrap in test environment, likely due to missing datasource config or a bean initialization failure cascading from the above issues.
- **Fix:** Verify `application-test.yml` has all required properties; fix the JWT test config first and re-run.

#### Test Coverage Gap
- **32 tests total** for an enterprise POS system is very low. Core modules (returns, purchase orders, inventory adjustments, reporting, dashboard) have no visible test coverage.
- **Verdict:** Test suite is not reliable enough to validate production readiness.
