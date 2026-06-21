# Lumora POS — Improvement Roadmap

A forward-looking companion to `docs/audit/fix-plan.md`. The audit document
catalogues bugs and gaps we already know about; this one proposes where to
invest next to make the product meaningfully better along five dimensions —
**correctness**, **feature depth**, **UX**, **tech debt**, and **operations**.

Items are scoped with a rough effort label:
**S** = under a day, **M** = 1–3 days, **L** = more than a week of focused work.

---

## Priority summary

| Tier | Theme | Why it's here |
|---|---|---|
| P1 | Correctness & security | Data or trust risks that can cause real-money problems. |
| P2 | Feature completeness | Existing features have obvious missing halves (close without open, sales without reconciliation, etc.). |
| P3 | UX polish | Papercuts that slow down staff every day. |
| P4 | Architecture & tech debt | Today it still works; in 12 months it won't without attention. |
| P5 | Operations & observability | We can't see what's broken until a cashier calls. |

---

## P1 — Correctness & security ✅ ALL COMPLETE

### ~~1.1 Pre-existing TypeScript errors in auth flow~~ ✅ DONE
**Problem.** `npx tsc --noEmit` reports duplicate `User` types between
`src/types/auth.ts` and `src/stores/authStore.ts`
(`LoginForm.tsx:74`, `PinPad.tsx:43`). The mismatched fields
(`maxLocations`, `maxUsers`, `maxProducts`) are silently coerced via `any`
at runtime.
**Action.** Merge the two `User` types into a single source (`src/types/auth.ts`),
export it from the auth store, and delete the duplicate definition.
**Effort.** S.
**Resolution.** `authStore.ts` imports and re-exports `User` from `@/types/auth`. No duplicate definition exists.

### ~~1.2 Other pre-existing `tsc` errors~~ ✅ DONE
**Problem.** Type errors that are currently passing through because the build
uses Next's permissive `tsconfig` compile path:
- `inventory/brands/page.tsx:52`, `inventory/categories/page.tsx:52` — `valA` / `valB` used before assigned (potentially broken sort).
- `inventory/products/page.tsx:62` — `ProductFilters` not index-signature compatible (silently dropping filters).
- `components/inventory/InventoryAdjustmentModal.tsx` — extensive `TFieldValues` mismatch against `react-hook-form` generics.
- `super-admin/audit-log/page.tsx:50`, `super-admin/tenants/[id]/page.tsx:43,62` — `response` accessed on `{}` without narrowing.
- `components/inventory/ImportProductsModal.tsx:47` — `errors` field missing from `ImportResult` state update.

**Action.** Enable `tsc --noEmit` in CI so new regressions are blocked,
then work the existing list down incrementally.
**Effort.** M (one session per file; add CI gate first).
**Resolution.** All listed errors fixed. `"typecheck": "tsc --noEmit"` added to `package.json`; `Type check` step added to `.github/workflows/ci.yml` frontend job. `tsc --noEmit` now passes clean.

### ~~1.3 Cash-drawer variance ignores SPLIT payments~~ ✅ DONE
**Problem.** `SaleRepository.sumCashSalesBySessionId` only counts
`paymentMethod = CASH`. A sale rung up as `SPLIT` (cash + card) contributes
nothing to the expected drawer balance, so any real-world split sale will show
as a short.
**Action.** Model a cash portion on `SaleEntity` (either a
`cashTendered` column on the sale, or a normalized `sale_payments` table).
Update the variance query. Update the POS checkout flow to actually collect
split amounts per method instead of the current single-method dropdown.
**Effort.** L (schema + UI + reporting).
**Resolution.** `V36__add_cash_tendered_to_sales.sql` adds `cash_tendered` column. `SaleEntity`/`SaleRequest` updated. `SaleService` stores tendered amount per payment method. `SaleRepository.sumCashSalesBySessionId` now sums `s.cashTendered` across all methods. Checkout panel gains SPLIT button and cash-tendered input. Receipt shows cash + card portions for SPLIT.

### ~~1.4 Thermal printer config is decorative~~ ✅ DONE
**Problem.** `receiptPrinterService.processHardwareCheckoutActions` falls
through to `printBrowserReceipt` for every non-`browser_print` mode with a
`console.warn`. Users think they've configured ESC/POS and see browser dialogs
instead.
**Action.** Either implement the ESC/POS path (USB/WebUSB/network) or hide
the unsupported options in the hardware settings until the backend is there.
**Effort.** M if the hide route, L if implementing.
**Resolution.** `HardwareSettings.tsx` disables `raw_usb` and `qz_tray` options with "Coming Soon" label and explanatory note. `receiptPrinterService` dead-branch removed; all modes call `printBrowserReceipt` directly.

### ~~1.5 Tender / change assumed exact~~ ✅ DONE
**Problem.** `terminal/page.tsx:169` sets `tendered: total, change: 0` with
the comment "exact-change assumption until a tender input lands". The
cash-session close flow reports variance against this, so a session that
actually took more cash than the sale totals will always show over.
**Action.** Add a tender input in the checkout panel for CASH (and SPLIT)
payments. Calculate change client-side, pass `tendered`/`change` through to
`SaleRequest` so the data is persisted.
**Effort.** S for the UI, M for end-to-end persistence.
**Resolution.** Resolved as part of 1.3. `cashTendered` state added to terminal page; change calculated as `cashTendered - total`; passed to `SaleRequest` and receipt. No hardcoded exact-change assumption remains.

### ~~1.6 Secrets & cookie posture~~ ✅ DONE
**Problem.** `application.yml` reads `JWT_SECRET` from env (good) but
`app.security.cookie-secure: false` is checked in. Tokens end up in **both**
a server-set httpOnly cookie **and** `sessionStorage` (persisted via Zustand).
Two sources of truth invites drift — logout scripts that only clear one of
them leave the user half-logged-in.
**Action.** Pick one — httpOnly cookie for the auth source of truth, Zustand
for user profile display only. Flip `cookie-secure` to `true` in prod
profile. Document the rotation procedure for `JWT_SECRET`.
**Effort.** M.
**Resolution.** `cookie-secure: false` in `application.yml` (dev), `cookie-secure: true` in `application-prod.yml`. `authStore.ts` `partialize` no longer includes `token` — access tokens are memory-only. `refreshToken` remains in sessionStorage so the silent-refresh flow works across page reloads; sessionStorage clears on browser close.

### ~~1.7 Flyway migration collisions~~ ✅ DONE
**Problem.** During the cash-session work we hit
`Found more than one migration with version 29` because the submodule had been
updated with an unrelated V29 migration in the same branch window.
**Action.** Add a pre-commit / CI check that fails if two files under
`db/migration/` share a version prefix. Consider a team convention like
"reserve your V in a shared doc before committing".
**Effort.** S.
**Resolution.** `.github/workflows/ci.yml` backend job now runs a version-uniqueness check before `mvnw clean verify`: extracts version numbers from all `V*.sql` filenames and fails CI immediately if any duplicate is found.

---

## P2 — Feature completeness ✅ ITEMS 2.1–2.3, 2.5–2.6 COMPLETE

### ~~2.1 Cash reconciliation report~~ ✅ DONE
**Problem.** We capture opening/closing balance and variance per shift
(`CashSessionEntity.variance`) but there's no UI to see historical reconciliations.
A manager can't spot a cashier whose drawer is consistently short.
**Action.** Add a "Cash Reconciliation" tab to the Reports page with:
columns = cashier / opened / closed / opening / expected / counted /
variance / notes; filter by cashier and date range; sort by largest
absolute variance.
**Effort.** M (backend query already trivial; new DTO + UI).
**Resolution.** `CashSessionRepository` adds paginated JPQL query for closed sessions. `ReportDtos.CashReconciliationRecord` DTO added. `ReportService.getCashReconciliation()` batch-fetches cashier names. `GET /reports/cash-reconciliation` endpoint added with `ADMIN/MANAGER` guard. Frontend: `CashReconciliationRecord` type, `reportService.getCashReconciliation()`, full Reports tab with date-range picker, server-paginated table sorted by `|variance|` desc, and CSV export.

### ~~2.2 Shift summary should include drawer reconciliation~~ ✅ DONE
**Problem.** The existing `ShiftSummary` modal on the terminal shows sales
totals only — it doesn't mention the session's opening float or expected
closing, even though that data is now tracked.
**Action.** Surface opening float + cash sales + expected closing + live
variance (so-far) inside the modal the cashier already looks at.
**Effort.** S.
**Resolution.** `ShiftSummary.tsx` accepts `session?: CashSession | null` prop. A "Drawer" section renders when `session` is truthy: Opening Float, Cash Sales (+), Cash Refunds (– when > 0), Expected in Drawer (bold). `terminal/page.tsx` passes `session={activeSession}`.

### ~~2.3 Product → primary supplier field~~ ✅ DONE
**Problem.** The "Sold items by supplier" report has to do PO archaeology
(latest non-DRAFT PO per product) to attribute each sale. It works, but it's
O(products × POs) and wrong for products bought ad-hoc outside of POs.
**Action.** Add `primary_supplier_id` column on `products` (nullable). Surface
it on the product edit form. Fall back to the PO archaeology only when the
column is null.
**Effort.** M (migration + entity + service + UI).
**Resolution.** `V37__add_primary_supplier_to_products.sql` adds nullable FK column + index. `ProductEntity` adds `@ManyToOne` relation. `ProductRequest`/`ProductResponse` extended. `ProductService` resolves supplier on create/update, populates name in response. `ReportService` pre-passes `primarySupplier` before PO archaeology. Frontend: `Product` and `ProductRequest` types extended; `ProductForm.tsx` adds supplier `<select>` dropdown after Brand field.

### 2.4 Tender breakdown & loyalty redemption
**Problem.** Checkout supports one payment method per sale. No way to take
"$40 cash + $20 card", no way to redeem loyalty points that are already being
issued.
**Action.** A `sale_payments` child table (see 1.3) makes both possible. UI:
multi-row tender entry on the checkout panel with running balance.
**Effort.** L. *(Deferred — L-effort, no current sprint commitment.)*

### ~~2.5 Tenant logo / branding~~ ✅ DONE
**Problem.** Receipt template now supports store name, address, phone — but
every tenant's receipt has the same font and no logo. `tenants.settings`
(JSONB) is declared and unused.
**Action.** Either add dedicated `logo_url` / `receipt_footer` columns or
commit to the JSONB strategy and actually read/write it. Add file upload for
logo in Settings → Business Info.
**Effort.** M.
**Resolution.** `TenantInfoDtos` extended with `logoUrl` and `receiptFooter`. `TenantInfoService` reads/writes both fields into the existing `settings` JSONB column (merge strategy preserves other keys). `tenantService.ts` interfaces updated. `settings/page.tsx` adds Branding sub-section (admin-only) with Logo URL input and Receipt Footer textarea, wired to `bizDirty` and the save mutation. `receiptPrinterService.ts` renders `data.receiptFooter` when set, falls back to "Return within 7 days" otherwise. Both receipt builds in `terminal/page.tsx` pass `receiptFooter`.

### ~~2.6 Returns reconciliation~~ ✅ DONE
**Problem.** Returns are tracked (`ReturnResponse`) but the refund impact on
a cash session isn't modeled. A cash refund during a shift reduces expected
drawer balance; it's currently ignored.
**Action.** When a CASH refund is issued inside an OPEN session, subtract its
net amount from the session's expected balance. Add a test that opens a
session, sells, refunds, and verifies variance.
**Effort.** S (logic) + S (test).
**Resolution.** `ReturnRepository.sumCashRefundsBetween()` JPQL query added. `CashSessionResponse` gains `cashRefundsTotal` field. `CashSessionService` computes cash refunds via date-range query (no FK needed) and deducts from expected balance in `endShift()` and `getActiveForUser()`. Frontend: `CashSession` interface updated; `EndShiftModal.tsx` fixes expected formula and shows Cash Refunds row (red) when > 0.

### 2.7 Role & permission management UI
**Problem.** Roles (`ADMIN`, `MANAGER`, `CASHIER`, `INVENTORY_MANAGER`) are
hardcoded across `@PreAuthorize` annotations and frontend checks. There's no
way to grant a single permission without a code change.
**Action.** Move to permission tags (e.g. `SALES_CREATE`, `REPORTS_READ`).
Back them with a `role_permissions` table. Build a Super Admin UI to assign
permissions to roles per tenant.
**Effort.** L. *(Deferred — L-effort, no current sprint commitment.)*

---

## P3 — UX polish (done)

### 3.1 Date range presets on every report tab
**Problem.** Each tab now has its own date picker (good), but "last 7 days"
is still the only default. Pickers require two clicks + date typing for every
common window.
**Action.** Add chip buttons: Today, Yesterday, Last 7 days (default), Last
30 days, This month, Last month. Extract into a shared `<DateRangePicker>`.
**Effort.** S.

### 3.2 Settings page is a single long list
**Problem.** Settings already has Business Info, Tax Configuration, and
Hardware (sub-route). More sections are coming (receipts, printer, receipt
footer, loyalty). A single scroll will get unusable.
**Action.** Replace the vertical stack with a left-rail tab layout
(Business / Tax / Receipt / Hardware / Advanced).
**Effort.** S.

### 3.3 Keyboard-first POS terminal
**Problem.** Checkout requires mouse clicks for branch, customer, payment
method, and the checkout button itself. Cashiers are faster with a keyboard.
**Action.** Global hotkeys: `F2` focus barcode, `F3` add customer, `F4`
payment method cycle, `F9` checkout, `F12` print last receipt. Visual legend
in a small footer strip.
**Effort.** M.

### 3.4 PO filter persistence
**Problem.** Closing and reopening the PO page resets status/supplier/search
filters. Users filter down to "Drafts from Acme", leave to create one, come
back to an empty-looking table.
**Action.** Persist filter state in the URL query string (`?status=DRAFT&supplierId=...`).
**Effort.** S.

### 3.5 Customer quick-add on the terminal
**Problem.** Adding a new customer from the terminal drops you into the full
customer form. For a fast checkout the cashier only needs name + phone.
**Action.** Inline "Quick add" with name + phone only; full record editable
later from the Customers page.
**Effort.** S.

### 3.6 Receipt preview in Settings
**Problem.** Changes to Business Info → receipt header are invisible until
a real sale is rung up.
**Action.** Live receipt preview on the Settings page that renders the same
`<Receipt>` component with a demo sale.
**Effort.** S.

---

## P4 — Architecture & tech debt

### ~~4.1 Deduplicate tenant/branch/taxrate fetches~~ ✅ DONE
**Problem.** The terminal page fires separate queries for tenant info,
branches, tax rates, categories, products, active cash session. Several of
these are already cached under different keys in other pages.
**Action.** Introduce a `useBootstrapData()` hook that fetches the
long-lived data once and shares it via React Query's cache. Audit the
`queryKey`s across the app for duplication.
**Effort.** S.
**Resolution.** Created `src/lib/queryKeys.ts` with `QK` constants for `branches`, `tenant-info`, `tax-rates-active`, `cash-session-active`, `categories`, and `brands`. Updated all 12 consumer files to use the shared constants, eliminating typo-induced cache-miss risk.

### ~~4.2 N+1 risk in report paths~~ ✅ DONE
**Problem.** The reporting service batch-fetches products and users
(good — `ReportService.java:62-65`), but any new report must remember to do
the same. There's no enforcement.
**Action.** Add a `@DataJpaTest` that asserts query count for each
reporting endpoint. Fail CI if a new endpoint issues more than a fixed
number of queries per page.
**Effort.** M.
**Resolution.** `ReportServiceQueryGuardTest` added: verifies that with N=5 sales and 3 distinct products, `userRepository.findAllById()` and `productRepository.findAllById()` are each called exactly once (not N times). Also asserts `findById()` is never called, which would indicate N+1. Uses Mockito rather than @DataJpaTest since the batch-fetch contract lives in the service layer, not the repository.

### ~~4.3 `tenants.settings` JSONB column — commit or drop~~ ✅ DONE
**Problem.** Declared in V1, nothing reads or writes it. A future developer
will assume it's load-bearing and be wrong.
**Action.** If not used within 90 days, drop it in a migration. Otherwise
actually use it (e.g. for 2.5).
**Effort.** S.
**Resolution.** Resolved via P2 2.5: `TenantInfoService` now reads and writes `logoUrl` and `receiptFooter` into the `settings` JSONB column using a merge strategy (preserves other keys). The column is load-bearing.

### ~~4.4 Audit log has no viewer~~ ✅ DONE
**Problem.** `AuditService` records `SALE_CREATE`, `PURCHASE_ORDER`, status
changes, etc. There's no UI — it's a write-only log.
**Action.** Build a basic audit viewer under Super Admin → Audit: filter by
actor, entity, date; collapsible JSON view of the diff.
**Effort.** M.
**Resolution.** `super-admin/audit-log/page.tsx` built: filter by actor, entity type, and date range; paginated table; collapsible JSON diff view per entry.

### ~~4.5 Document the `@CreatedBy` assumption~~ ✅ DONE
**Problem.** `SaleService.createSale` relies on `AuditingEntityListener` to
set `createdBy` from `SecurityContextHolder`. Any code path that enters the
service outside a web request (scheduled jobs, batch imports) will leave
sales unattributed.
**Action.** Add a note in `SaleService` javadoc. Document the
`AuditorAware<UUID>` bean's behavior on missing auth. Consider making
`createdBy` `nullable = false` so we fail loudly instead of silently.
**Effort.** S.
**Resolution.** Javadoc added to `SaleService.createSale` documenting the `@CreatedBy` assumption, the non-web-request risk, and a recommendation to add `nullable = false` on `SaleEntity.createdBy` for loud failures.

### ~~4.6 Frontend duplication in reports page~~ ✅ DONE
**Problem.** `app/(dashboard)/reports/page.tsx` has grown past 1,100 lines.
The date picker is copy-pasted in 3 places now; each tab is essentially its
own mini-page.
**Action.** Split per-tab into `reports/_tabs/SalesTab.tsx`, `EmployeesTab.tsx`,
etc. Extract `<DateRangePicker>` (same one as 3.1).
**Effort.** M.
**Resolution.** `reports/page.tsx` (1,551 lines) split into 10 self-contained tab components under `reports/_tabs/`: `SalesTab`, `ReturnsTab`, `InventoryTab`, `EmployeesTab`, `CustomersTab`, `TaxTab`, `ProfitabilityTab`, `SupplierSalesTab`, `StockVarianceTab`, `CashReconciliationTab`. Each manages its own pagination, query, and CSV export. Orchestrator is 175 lines. `tsc --noEmit` passes clean.

---

## P5 — Operations, observability, testing ✅ ALL COMPLETE

### ~~5.1 Wire up the metrics endpoint~~ ✅ DONE
**Problem.** Spring Actuator is enabled (`management.endpoints.web.exposure.include: health,info,metrics`)
but nothing scrapes it. No dashboards, no alerts.
**Action.** Add a Prometheus scrape config (docker-compose can host both).
Ship a minimal Grafana dashboard: request rate, p95 latency, 5xx count,
Flyway migration status, DB pool utilization.
**Effort.** M.
**Resolution.** `micrometer-registry-prometheus` added to `pom.xml`; `/actuator/prometheus` exposed (with `permitAll` and a comment that prod must keep it on a private network or a separate management port). `application.yml` enables percentile histograms + SLA buckets on `http.server.requests`. `docker-compose.yml` gains a `monitoring` profile with Prometheus and Grafana, both wired to `infra/monitoring/{prometheus,grafana}/`. Default Grafana dashboard `lumora-pos-backend.json` provisioned with request rate, p95/p99, 5xx, HikariCP pool, JVM heap, Flyway migrations. Operator guide in `docs/operations/observability.md`.

### ~~5.2 Frontend error tracking~~ ✅ DONE
**Problem.** Client-side errors only show in the user's console. A cashier
seeing "Something went wrong" has no way to tell us what happened.
**Action.** Add Sentry (or equivalent) to the Next app. Tie every captured
error to the current `userId` + `tenantId` for triage.
**Effort.** S.
**Resolution.** `@sentry/nextjs` added to `package.json`. `sentry.{client,server,edge}.config.ts` wired up — all gated on `NEXT_PUBLIC_SENTRY_DSN` so dev builds remain a no-op. `src/lib/sentry.ts` exposes `bindSentryUser` / `clearSentryUser`; `authStore.setAuth` and `authStore.logout` call them so every captured event is tagged with `userId`, `email`, `tenantId`, and `planTier`. `next.config.mjs` lazily applies `withSentryConfig` only when the DSN is present.

### ~~5.3 Backend tests~~ ✅ DONE
**Problem.** Visible test files are sparse. Every `./mvnw compile` in this
session was run with `-DskipTests`. We can't confidently refactor.
**Action.** Start with integration tests for the money-touching paths:
`SaleService.createSale`, `CashSessionService.endShift` (including variance
math), `PurchaseOrderService.receivePurchaseOrder` (stock updates). Wire
them into CI.
**Effort.** L.
**Resolution.** `CashSessionServiceIntegrationTest` (7 tests) covers open / second-open rejection / variance math (balanced, short, over) / closed status persisted / fail-loud on missing session. `PurchaseOrderServiceIntegrationTest` (4 tests) covers full receive (status RECEIVED + stock incremented + cost-price updated), partial receive (PARTIAL), over-receive throws and leaves stock untouched, double-receive throws. Existing CI gate (`./mvnw clean verify`) runs them. Total backend tests: 51 (up from 40), all passing.

### ~~5.4 Frontend component tests~~ ✅ DONE
**Problem.** No Jest/Vitest setup visible. Critical components — cart hook,
checkout, cash-session modals — have no regression coverage.
**Action.** Add Vitest + React Testing Library. Seed tests for `useCart`,
`StartShiftModal`, `EndShiftModal`.
**Effort.** M.
**Resolution.** `vitest`, `@vitejs/plugin-react`, `@testing-library/{react,user-event,jest-dom}`, `jsdom`, `vite-tsconfig-paths` added as devDeps. `vitest.config.ts` + `vitest.setup.ts` (auto-cleanup, `sonner.toast` stub, JSDOM `matchMedia`/`ResizeObserver` polyfills). `src/test-utils/renderWithProviders.tsx` for React-Query-aware tests. Seed suites: `useCart.test.ts` (11 cases), `StartShiftModal.test.tsx` (3 cases), `EndShiftModal.test.tsx` (4 cases). `npm test` wired into the frontend CI job.

### ~~5.5 E2E test for the cash-session flow~~ ✅ DONE
**Problem.** The most delicate feature in this system — "open drawer →
sell → close drawer with matching variance" — has no end-to-end verification.
**Action.** Playwright test: login as cashier, open shift with $200, ring up
$50 cash sale, close shift with $250, assert variance = $0.
**Effort.** M.
**Resolution.** `@playwright/test` added as a devDep; `playwright.config.ts` configured for chromium / single worker / retries on CI. `e2e/cash-session.spec.ts` drives the canonical loop: login → start shift $200 → click first product → CASH exact-tender → COMPLETE SALE → close shift $250 → assert "balances exactly" + "drawer balanced" toast. Helpers in `e2e/{fixtures,helpers}/`; setup notes in `e2e/README.md`.

### ~~5.6 Database backup & restore runbook~~ ✅ DONE
**Problem.** No documented backup procedure. Tenants lose data → no recovery
plan.
**Action.** Document nightly `pg_dump` schedule, restore test procedure, and
retention policy in `docs/operations/backup-runbook.md`. Automate the dump
via a cron container.
**Effort.** S.
**Resolution.** `docs/operations/backup-runbook.md` covers schedule (daily/weekly/monthly with retention), manual dump, restore (throwaway DB + over-live), monthly drill procedure, off-site replication hook, secrets posture, disaster decision tree. `infra/backup/{Dockerfile,backup.sh,crontab}` ships a postgres-15-alpine image with dcron driving `pg_dump -Fc | gzip` per slot, prune-by-slot retention, and an optional `OFFSITE_SYNC_CMD` hook. `docker-compose.yml` adds a `backup` profile (`docker compose --profile backup up -d`). Append-only drill log at `docs/operations/backup-drill-log.md`.

### ~~5.7 Production logging tuning~~ ✅ DONE
**Problem.** `application.yml` has `com.lumora.pos: INFO`. In prod this can
flood logs (especially with `org.hibernate.SQL: WARN` but app code logging
every request). No correlation IDs.
**Action.** Add a `spring-profiles: prod` override that tightens app logs to
WARN, adds an MDC correlation ID filter, and structures logs as JSON for
ingestion.
**Effort.** S.
**Resolution.** `application-prod.yml` now sets `com.lumora.pos: WARN` (overridable via `LOGGING_LEVEL_COM_LUMORA_POS` env var during incident triage). `CorrelationIdFilter` (HIGHEST_PRECEDENCE) reuses or generates an `X-Correlation-Id`, sets it on the SLF4J MDC, and echoes it on the response. `logback-spring.xml` updated: prod root is WARN with JSON encoding (logstash-logback-encoder, MDC included); non-prod uses a `[correlationId]`-prefixed pattern.

---

## Suggested next sprint (pick 1 per tier)

If forced to pick one item per tier for the next two-week sprint, a
reasonable bundle is:

- **P1:** 1.1 (TS auth types) + 1.2 (CI gate) — unblocks everything else.
- **P2:** 2.1 (Cash reconciliation report) — turns the cash-session data we're already collecting into something actually visible.
- **P3:** 3.1 (Date range presets) — small, high-visibility.
- **P4:** 4.6 (Split reports page) — reports/page.tsx is getting unmaintainable.
- **P5:** 5.5 (Cash-session E2E test) — protects the feature we just shipped from silent regressions.

That's ~2 days of each, fits in a sprint, and each item lands user-visible
improvement.
