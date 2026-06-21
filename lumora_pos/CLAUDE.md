# Lumora POS — Repo Guide for Claude

Multi-tenant SaaS Point-of-Sale system. Backend = Spring Boot 3.3 + Postgres + Flyway. Frontend = Next.js 14 (App Router) + React Query + Zustand + Tailwind. Both run side-by-side under `docker compose`.

> Authoritative roadmap & known gaps: `docs/improvement-roadmap.md` and `docs/audit/fix-plan.md` — read these before proposing architectural work.

---

## Layout

```
backend/                  # Spring Boot 3.3.7, Java 17, Maven wrapper
  src/main/java/com/lumora/pos/
    auth/                 # users, roles, JWT login + PIN login
    audit/                # AuditService + audit_log entries
    branch/               # multi-location store branches
    cashsession/          # open / close drawer (variance math lives here)
    common/               # BaseEntity, ApiResponse, exceptions
    config/               # SecurityConfig, JwtAuthenticationFilter,
                          # RateLimitFilter, CorrelationIdFilter, CacheConfig
    customer/  employee/  inventory/  purchase/  returns/  sales/  supplier/  tax/
    superadmin/           # cross-tenant management plane
    tenant/               # TenantContext (ThreadLocal<UUID>)
  src/main/resources/
    application.yml       # base config
    application-dev.yml   # local dev overrides
    application-prod.yml  # prod (cookie-secure, WARN logs, JSON output)
    logback-spring.xml    # JSON in prod, [correlationId] pattern elsewhere
    db/migration/V*.sql   # Flyway — currently up to V37
  src/test/java/...       # JUnit 5 + Spring Boot Test, H2 in-memory

frontend/                 # Next.js 14.2.35, React 18, TypeScript strict
  src/
    app/(auth)/           # /login + super-admin login
    app/(dashboard)/      # admin/manager UI: reports, inventory, customers, settings
    app/(pos)/terminal/   # cashier checkout terminal
    app/(super-admin)/    # cross-tenant admin
    components/           # ui/ (shadcn), pos/, auth/, inventory/
    hooks/                # useCart, useBarcodeScanner, usePosHotkeys
    lib/                  # apiError, queryKeys (QK constants), sentry, utils
    services/             # axios wrappers per domain (authService, saleService, …)
    stores/               # zustand: authStore, superAdminStore
    types/                # shared TS types
  e2e/                    # Playwright (cash-session.spec.ts is the canonical)

docs/
  audit/                  # active fix-plan + completed-fix records
  improvement-roadmap.md  # P1–P5 backlog (P1, P4, P5 complete; P2, P3 partial)
  operations/             # observability.md, backup-runbook.md

infra/
  backup/                 # pg_dump cron container (compose --profile backup)
  monitoring/             # Prometheus + Grafana (compose --profile monitoring)
```

---

## Common commands

### Stack

```bash
# Full stack
docker compose up -d
# Optional add-ons (off by default)
docker compose --profile monitoring up -d   # Prometheus :9090, Grafana :3001
docker compose --profile backup up -d       # nightly pg_dump
```

### Backend (`cd backend/`)

```bash
./mvnw spring-boot:run                          # dev with prod-like logging
./mvnw -B test                                  # unit + integration tests (H2)
./mvnw -B test -Dtest=CashSessionServiceIntegrationTest
./mvnw clean verify                             # what CI runs
```

### Frontend (`cd frontend/`)

```bash
npm install
npm run dev          # :3000
npm run typecheck    # tsc --noEmit (CI-gated)
npm run lint         # next lint (CI-gated)
npm test             # Vitest (CI-gated)
npm run test:watch
npm run test:e2e     # Playwright — needs the stack running
npm run build        # next build (CI-gated)
```

---

## Architecture, what's load-bearing

### Multi-tenancy

Every domain entity extends `common/entity/BaseEntity`, which carries `id`, `tenantId`, `createdAt/By`, `updatedAt/By`, `version`. `tenant/TenantContext.java` is a `ThreadLocal<UUID>` set by `JwtAuthenticationFilter` from the JWT claim and read by services. **Do not query without filtering by tenant** — repositories take `tenantId` explicitly (`findByIdAndTenantId`, etc.) so missing it is a code-review red flag.

### Auth

- Email/password and PIN login both issue JWT access + refresh tokens.
- Access token: in-memory only (Zustand `partialize` deliberately omits `token`).
- Refresh token: sessionStorage (clears on browser close).
- HttpOnly cookie also set server-side; cookie-secure flips on in `application-prod.yml`.
- `SecurityConfig.java` — public: `/api/v1/auth/**`, `/actuator/health`, `/actuator/prometheus` (private network only in prod). Super-admin: `hasRole("SUPERADMIN")`.
- **Login destination is decided by method, not role:** PIN login always lands on `/terminal` (`PinPad.tsx`), email/password always on `/overview` (`LoginForm.tsx`). The `passwordChangeRequired` first-login flow takes priority on both and routes to `/change-password`.
- **PIN stored two ways** (`UserManagementService.setPin`): a bcrypt hash in `users.pin` (verifies login) and a keyed blind-index in `users.pin_lookup` = HMAC-SHA256(pin, JWT secret) (per-tenant uniqueness check, migration V54). PINs are unique **per tenant**. Changing/setting a PIN (`POST /users/me/pin`) re-authenticates with the **account password**, not the old PIN — this also covers first-time PIN setup.

### PIN login & tenant resolution (`app.auth.mode`)

PIN login needs to know *which tenant* to search before it can match a PIN. The `app.auth.mode` flag (`AUTH_MODE` env, default `single`) selects how:

- **`single`** — one business per database. `AuthService.resolveSingleTenantId()` resolves the sole active tenant. Fails if there are 0 or >1 active tenants. This is the model for dedicated container-per-customer deploys.
- **`multi`** — many tenants share one database. The tenant is resolved from the shop **subdomain**, which the frontend sends as the `X-Tenant-Domain` header; the backend looks it up via `tenantRepository.findByDomainOrSlug(domain)` (case-insensitive; also matches `domain + '.lumora.com'`). Only that tenant's PINs are ever searched, so cross-tenant PIN collisions are structurally impossible.

**Cookie / same-origin requirement (load-bearing):** the route-guard in `middleware.ts` reads the httpOnly `auth-token` cookie the backend sets. In `multi` mode the frontend is on a subdomain (`shopa.example.com`) while the backend is a different host, so a cross-origin `Set-Cookie` lands on the wrong host and the guard bounces you back to `/login`. The fix (already wired for prod, and now for the Docker dev stack): serve the API **same-origin** via Next.js rewrites — set `BACKEND_URL` (rewrite target) and an **empty** `NEXT_PUBLIC_API_URL` (browser hits relative `/api/v1`). Both are baked at **build time** (`NEXT_PUBLIC_*` is inlined; rewrites freeze into the standalone server), so they're `Dockerfile` `ARG`s passed from compose `build.args`, not just runtime env.

**⚠️ Deploy guard:** the base `application.yml` default may be `multi` locally for subdomain testing. A single-tenant/container prod box **must** set `AUTH_MODE=single`, or PIN login there will demand an `X-Tenant-Domain` it never receives.

### Money paths (treat with care)

| Path | Service | Test |
|---|---|---|
| Sale → stock deduction → tax | `sales/SaleService.createSale` | `SaleServiceIntegrationTest` |
| Cash drawer open/close → variance | `cashsession/CashSessionService` | `CashSessionServiceIntegrationTest` |
| Purchase order → stock increase | `purchase/PurchaseOrderService.receivePurchaseOrder` | `PurchaseOrderServiceIntegrationTest` |
| Return → cash refund deducted from session expected balance | `returns/ReturnService` + `CashSessionService` | covered via cash-session integration |

**Variance formula:** `closing − (opening + cashSales − cashRefunds)`. Anything that touches this needs an integration test before merge.

`SaleEntity` carries a `cashTendered` column (V36) so SPLIT payments contribute their cash portion to the drawer-variance query.

### Migrations

Flyway, classpath `db/migration/V*.sql`, validated on boot. CI runs a duplicate-version check before `mvnw clean verify` (`.github/workflows/ci.yml`). Reserve your version number before committing — V-collisions across branches are a known recurring foot-gun.

### Frontend cache keys

`src/lib/queryKeys.ts` exports `QK.*` constants (`branches`, `tenant-info`, `tax-rates-active`, `cash-session-active`, `categories`, `brands`). Use these — typo-only string keys silently miss the cache.

### Observability

- `/actuator/prometheus` exposes Micrometer metrics; Grafana dashboard `lumora-pos-backend.json` provisioned under the `monitoring` profile.
- `CorrelationIdFilter` adds `X-Correlation-Id` to every response and to the SLF4J MDC. Prod logs are JSON (logstash-logback-encoder); dev logs prefix with `[correlationId]`. Ask users for the header value when triaging.

---

## Conventions

- **No comments** unless the *why* is non-obvious (hidden constraint, bug workaround, surprising behavior). Don't narrate what well-named code already says.
- **No backwards-compat hacks**: don't rename unused `_vars`, don't keep dead code with `// removed` markers, don't re-export to preserve old paths. Just delete.
- **No new abstractions for hypothetical needs** — three similar lines beat a premature helper.
- **`@PreAuthorize`** is the source of truth for role gates. Frontend `hasRole`/`hasFeature` checks are UX-only — never the security boundary.
- **Type errors block CI** (`npm run typecheck`). Don't `// @ts-ignore` to ship; fix the type.

---

## Where to look first

- "How is X authorized?" → `config/SecurityConfig.java` + `@PreAuthorize` on the controller.
- "What does the cashier see?" → `app/(pos)/terminal/page.tsx` + `components/pos/`.
- "What money math just changed?" → `git log` + the integration test for that service.
- "Why doesn't this query return data?" → check `tenantId` is being passed to the repository.
- "Is this feature gated by plan tier?" → `superadmin/interceptor/FeatureGuardInterceptor.java` + `<FeatureGuard feature="...">` on the React side.
