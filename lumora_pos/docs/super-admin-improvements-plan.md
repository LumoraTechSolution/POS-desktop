# Super Admin — Improvements Plan (P1 / P2 / P3)

## Context

The Super Admin control plane is the most-privileged surface in the Lumora POS multi-tenant platform — it provisions tenants, controls plan limits, and inspects audit data across the whole estate. Recent work fixed the malformed seed password hash (`V38__fix_super_admin_password.sql`) and the CSP-nonce hydration bug that was breaking the login form. With those out of the way, a deeper review of `superadmin/` (backend) and `(super-admin)/` (frontend) plus the original audit docs reveals a different picture than the docs claim:

- The `step59` audit reports mark Phase 0/1 hardening as "✅ DONE", but the code shows several of those items still open — the audit ticked off the *fix-plan* tasks without verifying the underlying behavior.
- Super admin authentication and tenant mutations are **not audited** to the `audit_log` table — only printed to slf4j. Forensics for "who suspended this tenant?" is impossible today.
- The super-admin JWT is persisted to **localStorage** while the tenant `authStore` deliberately keeps tokens in memory only. The most privileged account has the weakest token storage.
- Suspending a tenant doesn't take effect until existing JWTs expire (up to 24h), so suspended-tenant users keep transacting.

This plan groups improvements into three phases:

- **P1 — Security & correctness must-haves.** Block real-world risk and compliance gaps.
- **P2 — Operator UX & resilience.** Things a SaaS operator notices day-to-day.
- **P3 — Observability, finance correctness, polish.** Quality-of-life and reporting accuracy.

All phases preserve the architectural invariants stated in `step50_super_admin_control_panel.md`: super admins live in their own `super_admins` table, get JWTs with `tenantId: null`, and never share state with tenant auth.

---

## Verified gap snapshot

| Area | Gap | Evidence |
|---|---|---|
| Auth audit | No log of login / failure / logout for super admin | `SuperAdminAuthService.java:50,56,62,74` only `log.warn`/`log.info`; no `AuditService` call |
| Tenant audit | Suspend / activate / config-update / create-tenant not in `audit_log` | `SuperAdminTenantService.java:218,243,272,300` — only `log.info/warn`, no `AuditService` injection |
| Brute-force | No per-account lockout | `SuperAdminEntity` lacks `failedLoginAttempts` / `lockedUntil`; only `RateLimitFilter` per-IP |
| Session invalidation | Suspended tenant keeps working until JWT expires | `SuperAdminTenantService.java:252-274` flips `is_active` but `JwtAuthenticationFilter` doesn't re-check |
| Token storage | Super admin JWT in localStorage | `superAdminStore.ts:39-44` partializes `token`; tenant `authStore.ts:62-69` deliberately omits it |
| Token refresh | 401 = immediate logout for super admin | `superAdminApi.ts:37-50`; tenant `api.ts:56-113` has full refresh-with-queue |
| Logout | Cookie cleared client-side only | `app/api/super-admin-logout/route.ts:6-15` no backend invalidation call |
| Password change | No endpoint to change super-admin password | No `/super-admin/auth/password` route in `SuperAdminAuthController.java` |
| MFA | None | `SuperAdminEntity` lacks `mfaSecret` / `mfaEnabled` |
| MRR data | Magic numbers `*50 / *150 / *500` | `SuperAdminTenantService.java:325` |
| UX | `alert()` / `confirm()` for destructive actions | `tenants/page.tsx:56`, `tenants/[id]/page.tsx:64`, `ProvisionTenantModal.tsx:49` |
| Caching | Pages refetch on every navigation; no `QK.*` keys | `tenants/page.tsx:33-46`, `audit-log/page.tsx:36-58` use raw `useEffect` + axios |
| Tests | Almost no super-admin tests | `src/test/java/com/lumora/pos/superadmin/` has only `FeatureGuardInterceptorTest.java` |

---

## P1 — Security & correctness must-haves

### P1.1 — Audit super-admin auth events

**Why:** Today there is no record of who tried to log in as super admin, or whether they succeeded. Compliance and intrusion forensics are impossible.

**Files:**
- `pos-backend/src/main/java/com/lumora/pos/superadmin/service/SuperAdminAuthService.java` — inject `AuditService`, log success after line 68 and failure at the three `BadCredentialsException` sites (lines 51, 57, 63).
- `pos-backend/src/main/java/com/lumora/pos/audit/enums/AuditAction.java` — add `SUPER_ADMIN_LOGIN`, `SUPER_ADMIN_LOGIN_FAILED`, `SUPER_ADMIN_LOGOUT` if not already present.
- `pos-backend/src/main/java/com/lumora/pos/superadmin/controller/SuperAdminAuthController.java` — add `POST /logout` that clears the cookie *and* writes the audit record (current implementation at `app/api/super-admin-logout/route.ts` only clears the Next.js cookie).

**Reuse:** `audit/service/AuditService.java` already provides `logAuthEvent` / `log(...)` for tenant-side. Pass `tenantId = null` for super-admin events; `audit_log.tenant_id` is already nullable per `V24` schema.

### P1.2 — Audit super-admin tenant mutations

**Why:** A super admin can suspend a paying customer, change their plan, or reset limits with **zero audit trail**. Discoverability after the fact is impossible.

**Files:**
- `pos-backend/src/main/java/com/lumora/pos/superadmin/service/SuperAdminTenantService.java` — inject `AuditService`. Add audit calls inside the four mutating methods:
  - `createTenant` (line 218) → `AuditAction.CREATE`, entity `TENANT`
  - `updateTenantConfiguration` (line 243) → `AuditAction.UPDATE`, capture before/after snapshots of `TenantConfigurationEntity`
  - `suspendTenant` (line 272) → `AuditAction.UPDATE`, entity `TENANT_CONFIG`, reason: `"SUSPEND"`
  - `activateTenant` (line 300) → `AuditAction.UPDATE`, entity `TENANT_CONFIG`, reason: `"ACTIVATE"`
- Audit row should record the **super admin's** id (not `tenantId`) — extend `AuditService` if needed to accept an explicit actor.

**Reuse:** `before/after` JSON serialization helper already exists in `AuditService`. Use the same pattern as tenant-side `update` calls.

### P1.3 — Account lockout for super admin

**Why:** `RateLimitFilter` is per-IP, which is bypassable from a botnet or a single shared NAT. The most privileged account should also have a per-account counter.

**Files:**
- `pos-backend/src/main/resources/db/migration/V39__super_admin_lockout_columns.sql` — add `failed_login_attempts INT NOT NULL DEFAULT 0`, `locked_until TIMESTAMP`.
- `pos-backend/src/main/java/com/lumora/pos/superadmin/entity/SuperAdminEntity.java` — add the matching fields.
- `SuperAdminAuthService.java` — on failed password, increment counter; on success, reset to 0; lock for 15 min after 5 failures (configurable via `app.security.super-admin.max-attempts` / `lockout-duration-minutes`).
- Throw a distinct `BadCredentialsException("Account temporarily locked")` so the frontend can display a clearer message (still don't leak whether the email exists).

### P1.4 — Token storage parity with tenant authStore (XSS hardening)

**Why:** A reflected XSS anywhere in the super-admin shell exfiltrates a 24h JWT from localStorage. The tenant authStore solved this exact problem by keeping access tokens memory-only — super admin should match.

**Files:**
- `pos-frontend/src/stores/superAdminStore.ts` — change `partialize` (line 40-44) to omit `token`. Persist only `user` and `isAuthenticated` (rehydrate token via silent refresh on app mount, mirroring `authStore.ts:62-69`).
- `pos-frontend/src/services/superAdminApi.ts` — port the request-queuing refresh logic from `services/api.ts:56-113`. On 401, attempt `POST /super-admin/auth/refresh`; only logout if refresh also fails.
- `pos-backend/src/main/java/com/lumora/pos/superadmin/controller/SuperAdminAuthController.java` — add `POST /refresh` reading the httpOnly `sa-auth-token` cookie + a new `sa-refresh-token` cookie (or sessionStorage refresh token, depending on what tenant-side does — confirm during implementation).
- `pos-frontend/src/app/api/super-admin-logout/route.ts` — call backend `/auth/logout` (P1.1) before clearing the cookie so the server can invalidate / audit.

### P1.5 — Suspend-tenant takes effect immediately

**Why:** `suspendTenant` flips `is_active=false` but the tenant's existing JWTs stay valid until expiry (up to 24h). A "suspended" customer can still ring up sales for hours.

**Files:**
- `pos-backend/src/main/java/com/lumora/pos/config/JwtAuthenticationFilter.java` — after token validation, look up the tenant configuration (cache-backed; the `tenantConfigs` cache referenced by `@CacheEvict` in `SuperAdminTenantService.java:227` already exists). If `!config.isActive()`, reject with 401 `"Tenant suspended"`.
- This automatically picks up `@CacheEvict` invalidation when super admin toggles state — no extra wiring needed.

### P1.6 — Force password change on first super-admin login

**Why:** The seed password is now valid (post-V38) but is also publicly documented (`SuperAdmin@2024`). Without a forced change, a fresh deployment is one `git clone` away from compromise.

**Files:**
- `V40__super_admin_password_change_required.sql` — add `password_change_required BOOLEAN NOT NULL DEFAULT TRUE`. Backfill existing rows to `TRUE` so the seeded admin must rotate on first login.
- `SuperAdminEntity.java` + `SuperAdminAuthService.java` — when flag is set, login still succeeds but issues a *short-lived* token (5 min) scoped to a single endpoint: `POST /super-admin/auth/change-password`. All other routes 403 until the flag is cleared.
- Frontend: after login, if `superAdmin.passwordChangeRequired`, route to `/super-admin/change-password` instead of `/super-admin`.

---

## P2 — Operator UX & resilience

### P2.1 — MFA / TOTP for super admin

**Why:** With password + audit + lockout in place, the remaining single point of failure is the password itself. TOTP is the highest-leverage security improvement that doesn't require a third-party identity provider.

**Files:**
- `V41__super_admin_mfa_columns.sql` — `mfa_secret VARCHAR(64)`, `mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE`, `mfa_backup_codes TEXT[]` (hashed).
- `SuperAdminEntity.java` + `SuperAdminAuthService.java` + new `SuperAdminMfaService.java` — TOTP generation (use `eu.mihosoft.j8plus` or `com.warrenstrange:googleauth:1.5.0`; pick whichever is already on the classpath, otherwise add `googleauth`), enrollment (`POST /super-admin/auth/mfa/enroll` returns provisioning URI + QR data), verification (`POST /super-admin/auth/mfa/verify`), and a two-step login: first password → if `mfa_enabled`, return a short-lived "mfa-pending" token; client posts TOTP code; server upgrades to a real session.
- Backup codes: 10 single-use codes shown once at enrollment, hashed in DB.
- Frontend: `app/(super-admin)/super-admin/mfa-setup/page.tsx` for enrollment; modify login flow to handle the `mfa-pending` state.

### P2.2 — Self-service password change

**Files:** `POST /super-admin/auth/change-password` (used by both P1.6 and self-service); UI page `app/(super-admin)/super-admin/account/page.tsx`. Validate strength via the same `BCryptPasswordEncoder(12)` as login.

### P2.3 — Last-login IP / user-agent tracking

**Files:** Add `last_login_ip` / `last_login_user_agent` to `super_admins` (`V42`). Populate in `SuperAdminAuthService.login()` from the `HttpServletRequest`. Show on the new `account/page.tsx` so admins can spot anomalies.

### P2.4 — Replace native `alert()` / `confirm()` with shadcn Dialog + sonner

**Files:**
- New `pos-frontend/src/components/super-admin/ConfirmDialog.tsx` (or extract a `useConfirmDialog()` hook).
- Replace usages in `tenants/page.tsx:56`, `tenants/[id]/page.tsx:64`, `ProvisionTenantModal.tsx:49`.
- Use `sonner` toasts for success/failure (already imported globally in `app/layout.tsx` Toaster).

### P2.5 — React Query for super-admin pages

**Why:** Today every navigation refetches. There's also a race condition in `tenants/page.tsx:33-46` (concurrent `loadTenants` calls during fast pagination clicks).

**Files:**
- `pos-frontend/src/lib/queryKeys.ts` — add `QK.superAdmin.tenants(filters)`, `QK.superAdmin.tenantDetail(id)`, `QK.superAdmin.auditLog(filters)`, `QK.superAdmin.platformStats`.
- Convert the four pages (`tenants/page.tsx`, `tenants/[id]/page.tsx`, `audit-log/page.tsx`, `super-admin/page.tsx`) to `useQuery` and mutations to `useMutation` with `onSuccess: invalidateQueries`.
- Reuse the existing `QueryProvider` in `pos-frontend/src/components/providers/QueryProvider.tsx`.

### P2.6 — Shared error helper + PlanBadge component

**Files:**
- `pos-frontend/src/lib/utils.ts` — add `getApiErrorMessage(err: unknown): string` to replace the repeated `(err as { response?: { data?: { message?: string } } })?.response?.data?.message` cast in `login/page.tsx:28`, `tenants/[id]/page.tsx:43-44`, `ProvisionTenantModal.tsx:49`.
- `pos-frontend/src/components/super-admin/PlanBadge.tsx` — extract the duplicated badge JSX in `tenants/page.tsx:70-81` and `tenants/[id]/page.tsx:144-154`.

---

## P3 — Observability, finance correctness, polish

### P3.1 — Dashboard KPIs that match operator needs

`super-admin/page.tsx:43-74` shows MRR / active / suspended / tier counts. Add:
- **MAU** — count of distinct `users.id` with a session/audit row in the last 30 days, grouped by tenant.
- **Churn (this month)** — tenants whose `is_active` flipped to `false` this month.
- **Plan motion** — upgrades and downgrades over the last 30 days (requires recording plan-tier changes in audit log — depends on P1.2).
- **Error-rate heatmap per tenant** — count of 5xx in the last 24h, joined to tenant via correlation-id MDC.

Backend: extend `SuperAdminTenantService.getPlatformStats()` (line 308-338) and add new endpoints. Frontend: new dashboard cards.

### P3.2 — Audit log filters + CSV export

`audit-log/page.tsx` already has date range + search. Add:
- Action-type multi-select dropdown (drives `?action=...` filter).
- Actor (super-admin vs tenant user) filter.
- "Export CSV" button — backend `GET /super-admin/audit/export?format=csv` with a streaming `ResponseEntity<StreamingResponseBody>`.

### P3.3 — MRR grounded in real plan prices

`SuperAdminTenantService.java:325` hardcodes `(small * 50) + (medium * 150) + (enterprise * 500)`. Move to a `plan_prices` table seeded by migration, or — if simpler — `application.yml` under `app.billing.prices`. Lets finance change pricing without a code deploy.

### P3.4 — Loading skeletons on tenant detail

Replace the spinner at `tenants/[id]/page.tsx:70-79` with skeleton cards mirroring the layout. Reduces perceived latency. Reuse shadcn `<Skeleton>`.

### P3.5 — Tests

`src/test/java/com/lumora/pos/superadmin/` currently has only `FeatureGuardInterceptorTest`. Add:
- `SuperAdminAuthServiceTest` — login success, wrong password, deactivated account, lockout (after P1.3), MFA flow (after P2.1).
- `SuperAdminTenantServiceTest` — create / suspend / activate, asserts `AuditService` is called (after P1.2).
- `SuperAdminAuthControllerTest` — `@WebMvcTest` for happy + 401 + lockout responses.

Frontend: Vitest for the new `useConfirmDialog`, `getApiErrorMessage`, and `PlanBadge`. Playwright e2e for super-admin login → tenants list → suspend → re-login flow.

---

## Existing utilities to reuse (do not reinvent)

- **`audit/service/AuditService.java`** — already supports nullable `tenant_id`; just inject and call.
- **`config/JwtAuthenticationFilter.java`** — single chokepoint for the suspend-takes-effect-immediately check (P1.5).
- **`config/JwtTokenProvider.java`** — has `generateSuperAdminToken`; add a sibling `generateMfaPendingToken(superAdminId)` for the two-step login.
- **`config/CacheConfig.java`** + `tenantConfigs` cache — already wired with `@CacheEvict` in `SuperAdminTenantService`. Reuse for P1.5 lookups.
- **`pos-frontend/src/services/api.ts:56-113`** — copy-and-adapt the refresh-with-queue logic for `superAdminApi`.
- **`pos-frontend/src/stores/authStore.ts`** — reference implementation of memory-only token + sessionStorage refresh.
- **`pos-frontend/src/lib/queryKeys.ts`** — extend the `QK` constant rather than introducing new key strings.
- **`sonner` Toaster** — already mounted in `app/layout.tsx`; just call `toast.success/error`.
- **shadcn `<Dialog>` / `<Skeleton>`** — already in `components/ui/`.

---

## Verification

End-to-end checks per phase. Run the stack via `docker compose up -d` (compose now points at sibling repos after the recent infra refactor).

**P1.1 / P1.2:**
```sql
-- after a few super-admin actions
SELECT action, entity_type, user_id, created_at FROM audit_log
WHERE action LIKE 'SUPER_ADMIN_%' OR entity_type IN ('TENANT', 'TENANT_CONFIG')
ORDER BY created_at DESC LIMIT 20;
```
Expect rows for login, failed login, suspend, activate, config update.

**P1.3:** From the login UI, fail 5 times → 6th attempt returns `"Account temporarily locked"`. After 15 min, unlocks.

**P1.4:** With DevTools Application tab open, log in → confirm `localStorage.lumora-super-admin-auth` does **not** contain a `token` field. Refresh the page → user stays logged in (silent refresh hits backend). Tab close → reopen → must re-login (sessionStorage cleared).

**P1.5:** Log in as a tenant user → super admin suspends that tenant → tenant user's next API call returns 401 (within seconds, not hours).

**P1.6:** Fresh DB → log in as `superadmin@lumora.com` → forced to `/super-admin/change-password` → after change, full access.

**P2.1 (MFA):** Enroll → scan QR with Google Authenticator → log out → log back in → password accepted → TOTP prompt → enter code → in. Wrong code 3× → re-prompt password.

**P3.1 / P3.2 / P3.5:** New dashboard cards render with realistic numbers; CSV export downloads with all visible filter rows; `./mvnw test` passes the new suites; `npm run test:e2e` includes the suspend flow.

---

## Explicitly deferred / out of scope

These come up adjacent to this work but are **not** in this plan:

- Multiple super-admin roles (e.g., "audit-only"). One `SUPERADMIN` role is fine for v1.
- API keys / service accounts for super admin. JWT-only is sufficient.
- Tenant data export/import. Separate workstream.
- SAML / OIDC SSO for super admin. Wait for a customer ask.

## Open implementation questions to resolve at PR time

- For P1.4, does the tenant-side use a `refresh-token` cookie or sessionStorage? Confirm during implementation and mirror exactly to keep the two flows consistent.
- For P2.1 MFA, prefer `googleauth` (well-known) unless a TOTP lib is already on the classpath — check `pos-backend/pom.xml` first.
- For P3.1 churn, decide whether "churn" = `is_active` flip OR `subscription_end < now()`. Likely both, surfaced as separate metrics.
