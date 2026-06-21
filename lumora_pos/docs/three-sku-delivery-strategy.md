# Lumora POS — Three-SKU Delivery Strategy

> Status: **Approved plan, not yet implemented.** Companion to `docs/improvement-roadmap.md`.
> Supersedes the offline-queue narrative in `documentation/step66_electron_*.md`; hardware/IPC sections of those docs remain valid.

## Context

Lumora POS today ships as a single multi-tenant SaaS: Spring Boot 3.3 + Postgres + Flyway backend, Next.js 14 frontend, both run under `docker compose`. The team needs to package the same product into **three commercial SKUs** so that a sales call can offer the right one:

1. **Web** — fully online SaaS (cloud-hosted). Essentially what exists today.
2. **Desktop** — fully local, runs entirely inside the client's store. No internet required.
3. **Hybrid** — local-first with cloud sync. Cashier keeps working through internet outages; cloud is a mirror used for HQ reporting and backup.

The existing `documentation/step66_electron_*.md` series sketches an Electron desktop app but has zero code, no embedded-DB choice, and assumes a client-side localStorage offline queue. With the decisions captured below, that approach is replaced with a **server-side embedded Postgres + multi-terminal LAN** model.

The codebase is already well-suited to this split:

- `BaseEntity` uses **UUID** ids (no sequence conflicts on sync) — `backend/src/main/java/com/lumora/pos/common/entity/BaseEntity.java`
- Tenant isolation is explicit via `TenantContext` ThreadLocal — `backend/src/main/java/com/lumora/pos/tenant/TenantContext.java`
- JWT validation is self-contained (HS256, no remote call) — `backend/src/main/java/com/lumora/pos/config/JwtTokenProvider.java`
- Every entity carries `tenantId` + `version` (optimistic locking) → schema is carve-out-friendly and conflict-detection-friendly.

## Decisions locked

| Decision | Choice |
|---|---|
| Desktop topology | **Multi-terminal LAN**: one "Store Server" PC runs backend + DB; other terminals are thin clients on the same LAN |
| Hybrid sync model | **Local-first, cloud is the mirror**: writes hit local Postgres first, sync agent pushes to cloud asynchronously |
| Sequencing | **Desktop → Web → Hybrid** |
| Embedded DB | **Embedded Postgres** (bundle Postgres binary inside the installer; same engine as cloud → zero divergence) |

## Recommended approach: one codebase, three deployment profiles

Do **not** fork the codebase. Add a Spring profile and a frontend runtime flag that switch behaviour:

```
backend Spring profile:    cloud    | local    | local-sync
frontend deployment mode:  web      | desktop  | hybrid     (fetched from /api/v1/deployment/info)
```

| SKU | Backend runs where | DB | Profile | Sync engine |
|---|---|---|---|---|
| Web | Cloud | Cloud Postgres | `cloud` | off |
| Desktop | Store Server PC | Embedded Postgres | `local` | off |
| Hybrid | Store Server PC | Embedded Postgres | `local-sync` | on (push-only to cloud) |

Desktop and Hybrid share **~95% of code** — Hybrid is just Desktop with the sync module enabled and a `cloud.sync.url` configured at install time.

## Architecture per SKU

### SKU 1 — Web (existing, hardening only)

- No code changes to runtime. This is the current `docker compose up -d` deployment moved to cloud hosting.
- Deliverable is mostly **infra + ops**: managed Postgres, TLS, backups, monitoring (already designed under `infra/monitoring`, `infra/backup`).
- Frontend `deployment-info` endpoint returns `mode: "web"` → superadmin UI visible, no sync indicator.

### SKU 2 — Desktop (multi-terminal LAN, fully offline)

**Topology in a real store:**

```
[Store Server PC]                    [Cashier Terminal 1]
  Spring Boot (port 8081)            Electron shell
  Embedded Postgres (port 5433)      → http://store-server:8081
  Electron shell (optional)
                                     [Cashier Terminal 2]
                                     Electron shell or browser
                                     → http://store-server:8081
```

**Two installer artifacts:**

- **Lumora Store Server** — bundles JRE + Spring Boot fat jar + Postgres binary + initial schema. Built with `jpackage`. Boots Postgres as a managed child process, runs Flyway, starts Spring Boot. Can optionally also run the cashier UI locally.
- **Lumora Cashier Terminal** — small Electron installer (~80 MB). On first launch prompts for `http://<store-server-ip>:8081` and stores it. After that, identical UX to the web version.

**Backend changes:**

- New Spring profile `local` (in `application-local.yml`) with:
  - Datasource pointing to embedded Postgres (`jdbc:postgresql://localhost:5433/lumora`)
  - `lumora.tenant.mode=fixed` and `lumora.tenant.fixed-id=<uuid>` (set by the installer)
  - Superadmin module gated off (controllers `@ConditionalOnProperty(name="lumora.superadmin.enabled")`)
- New module `backend/src/main/java/com/lumora/pos/deployment/`:
  - `DeploymentInfoController` exposes `GET /api/v1/deployment/info` returning `{mode, version, syncEnabled, lastSyncAt}`
  - `FixedTenantContextInitializer` (only under `local` profile) populates `TenantContext` from config, bypassing JWT extraction for the tenant claim
- `JwtAuthenticationFilter` continues to validate users normally — login is still required per terminal.

**Frontend changes:**

- New service `frontend/src/services/deploymentService.ts` calls `/api/v1/deployment/info` once at app boot, caches in a Zustand store
- New component `frontend/src/components/layout/DeploymentBadge.tsx` — small chip showing "Local" / "Online" / "Hybrid (synced 2 min ago)"
- Hide superadmin nav under `mode !== "web"` (in `app/(dashboard)/layout.tsx`)
- Existing `useBarcodeScanner`, `usePosHotkeys`, `useCart` hooks need no changes

**Electron shell** (new — `electron/` at repo root):

- `electron/main.ts` — creates BrowserWindow, loads `http://localhost:3000` (dev) or static export (prod)
- `electron/preload.ts` — exposes hardware bridge (printer via `serialport`, cash-drawer kick code, USB scanner pass-through)
- Hardware integration follows the IPC pattern already documented in `documentation/step66_electron_patterns_reference.md`
- Auto-update via `electron-updater` against a GitHub Releases or self-hosted update feed

**Embedded Postgres approach:**

- Two viable options: the **`io.zonky.test:embedded-postgres`** library, or shipping a pre-extracted Postgres binary in the installer and starting it via `ProcessBuilder`.
- Recommendation: **vendor a Postgres binary** (more control, no Maven runtime dependency, easier to upgrade in lockstep with cloud Postgres version).
- Data dir: `%APPDATA%\Lumora\postgres-data` (Windows) / `~/Library/Application Support/Lumora/postgres-data` (macOS).
- Flyway runs on every boot exactly like cloud → zero migration divergence (this is the whole point of choosing Postgres over SQLite).

**Files to create:**

- `backend/src/main/resources/application-local.yml`
- `backend/src/main/java/com/lumora/pos/deployment/DeploymentInfoController.java`
- `backend/src/main/java/com/lumora/pos/deployment/DeploymentMode.java` (enum)
- `backend/src/main/java/com/lumora/pos/tenant/FixedTenantContextInitializer.java`
- `backend/src/main/java/com/lumora/pos/embedded/EmbeddedPostgresLifecycle.java` (Spring lifecycle hook to start/stop the bundled binary; only loaded under `local` profile)
- `electron/main.ts`, `electron/preload.ts`, `electron/package.json`, `electron/forge.config.js`
- `frontend/src/services/deploymentService.ts`
- `frontend/src/components/layout/DeploymentBadge.tsx`
- `installer/store-server.jpackage.json`, `installer/cashier-terminal.electron-builder.yml`

**Files to modify:**

- `backend/src/main/java/com/lumora/pos/config/SecurityConfig.java` — gate superadmin endpoints by profile
- `backend/src/main/java/com/lumora/pos/superadmin/**` — wrap with `@ConditionalOnProperty`
- `frontend/src/app/(dashboard)/layout.tsx` — hide superadmin nav for non-web modes
- `frontend/next.config.mjs` — add `output: 'export'` flag behind an env var so Electron can ship the static bundle
- `documentation/step66_electron_*` — revise: replace the localStorage-queue narrative with the embedded-Postgres + LAN model

### SKU 3 — Hybrid (Desktop + cloud sync)

**Same install as Desktop**, plus the sync engine activated. Configured at install time with a cloud tenant URL + API key.

**Sync design (local-first, push-mostly):**

Use the **transactional outbox** pattern. Every write that needs to reach the cloud also inserts a row into `sync_outbox`:

```sql
CREATE TABLE sync_outbox (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  entity_type VARCHAR(64) NOT NULL,    -- 'sale','return','cash_session','product_update', ...
  entity_id UUID NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  synced_at TIMESTAMPTZ,
  attempt_count INT NOT NULL DEFAULT 0,
  last_error TEXT
);
CREATE INDEX idx_sync_outbox_unsynced ON sync_outbox (created_at) WHERE synced_at IS NULL;
```

A `SyncAgent` background bean (only active under `local-sync` profile) polls the outbox every N seconds, batches unsynced rows, POSTs them to a cloud endpoint `/api/v1/sync/ingest`, and marks them synced on 2xx.

**Cloud-side ingest endpoint:**

- New cloud-only controller `SyncIngestController`
- Validates the API key → resolves to tenant
- For each entity in the batch, performs an idempotent upsert (UUIDs match → no duplicates)
- Sales and returns are **insert-only** in the domain model → trivially idempotent, no conflict resolution needed
- Products, customers, tax rates: `version`-aware upsert (last-writer-wins by `updated_at`, but if cloud `version > local version`, log and let the HQ-side win — products are HQ-managed)

**Pull side (cloud → local) — small surface, low frequency:**

- Daily or on-demand: agent calls `GET /api/v1/sync/products?since=<cursor>` to pick up product master / price changes pushed from HQ
- Cursor stored in a `sync_cursor` table

**Stock — handled separately (the hard one):**

- Don't try to live-sync stock counts. Each store maintains its own stock locally; cloud aggregates via the sale/return event stream.
- HQ dashboard reads cloud-side `current_stock` rebuilt from synced sales + receipts.
- Periodic stock-take reconciliation pushes `stock_adjustment` events upstream.
- This sidesteps the worst conflict scenario (two stores selling the "last unit" simultaneously is solved by per-store inventory).

**Files to create (Hybrid, on top of Desktop):**

- `backend/src/main/resources/db/migration/V38__sync_outbox.sql`
- `backend/src/main/java/com/lumora/pos/sync/SyncAgent.java` (scheduled, `@ConditionalOnProperty("lumora.sync.enabled")`)
- `backend/src/main/java/com/lumora/pos/sync/SyncOutboxRepository.java`
- `backend/src/main/java/com/lumora/pos/sync/OutboxPublisher.java` — small helper called from `SaleService.createSale`, `ReturnService.processReturn`, `CashSessionService.closeSession`
- `backend/src/main/java/com/lumora/pos/sync/SyncIngestController.java` (cloud-only, `@ConditionalOnProperty(name="lumora.deployment.mode", havingValue="cloud")`)
- `backend/src/main/resources/application-local-sync.yml`

**Files to modify:**

- `backend/src/main/java/com/lumora/pos/sales/SaleService.java` — call `OutboxPublisher.publish(sale)` after commit (use `@TransactionalEventListener` so the outbox row only goes out if the sale committed)
- Same wiring in `cashsession/CashSessionService.java`, `returns/ReturnService.java`, `purchase/PurchaseOrderService.java`
- `frontend/src/components/layout/DeploymentBadge.tsx` — show last sync timestamp + queue depth from `/api/v1/deployment/info`

## Reuse from existing code

- **Hardware/IPC patterns** — keep what's in `documentation/step66_electron_patterns_reference.md` (printer/scanner/drawer via preload bridge). Only the offline-queue narrative needs replacement.
- **`BaseEntity` + UUID ids** — already perfect for sync; no changes.
- **`TenantContext`** — extend with `FixedTenantContextInitializer` for `local` mode, don't replace.
- **`@PreAuthorize` role gates** — unchanged across all three SKUs.
- **Money math (`CashSessionService` variance, `SaleService` stock deduction)** — unchanged. The whole reason for choosing embedded Postgres is so this code runs identically on cloud and local.
- **Existing integration tests** (`SaleServiceIntegrationTest`, `CashSessionServiceIntegrationTest`, `PurchaseOrderServiceIntegrationTest`) — keep using H2; they test logic, not deployment.

## Build & release pipeline

- `mvn -P cloud package` → existing fat jar for the cloud Docker image (no change)
- `mvn -P local package` → fat jar with embedded-postgres lifecycle bean + local profile bundled
- `npm run build:web` → existing Next.js cloud build
- `npm run build:desktop` → `next build && next export` → static bundle consumed by Electron
- New top-level `installers/` directory:
  - `installers/store-server/` — `jpackage` config produces `LumoraStoreServer-Setup.exe`, `.dmg`, `.deb`
  - `installers/cashier-terminal/` — `electron-builder` config produces `LumoraCashier-Setup.exe`, `.dmg`, `.AppImage`
- CI extension in `.github/workflows/`: add `desktop-build.yml` triggered on tags matching `desktop-v*`

## Verification (per SKU)

**Web:**

- Existing CI (`./mvnw clean verify`, `npm run typecheck && npm run lint && npm test && npm run build`) — already gating
- Manual: deploy to staging, run Playwright suite from `frontend/e2e/`

**Desktop:**

- New integration test `EmbeddedPostgresLifecycleTest` — boots embedded Postgres, runs Flyway, runs a sale, asserts variance math
- Manual install on a clean Windows VM: install Store Server, install Cashier Terminal on a second VM in the same VirtualBox NAT network, log in from the terminal, run a full shift (open drawer → 3 sales → 1 return → close drawer), confirm variance is correct
- Pull the network cable mid-shift → all operations continue → reconnect → no data loss

**Hybrid:**

- Local integration test that uses a Wiremock'd cloud endpoint to verify the outbox publishes after commit and not after rollback
- End-to-end: run Store Server in Hybrid mode pointed at a staging cloud, run a shift offline (firewall-block the cloud), then unblock and verify all events arrive in cloud within one sync cycle, with no duplicates and matching totals
- Negative test: publish twice (simulate retry after timeout) → cloud upsert is idempotent

## Suggested execution order (Desktop-first)

1. **Backend profile split** — `local` profile, `FixedTenantContextInitializer`, deployment-info endpoint, superadmin gating. ~3 days.
2. **Embedded Postgres lifecycle** — vendor a Postgres binary, write the start/stop bean, prove Flyway runs against it. ~3 days.
3. **Electron shell** — minimal main + preload + static export, runs against the local backend. ~2 days.
4. **Hardware bridge** — port the patterns from step66 docs into actual `electron/preload.ts`. ~3 days.
5. **`jpackage` Store Server installer** + `electron-builder` Cashier installer. ~3 days.
6. **Multi-terminal LAN install test** on two VMs — fix any networking/CORS/binding issues found. ~2 days.
7. **Revise step66 docs** to match the shipped architecture; archive the localStorage-queue narrative. ~1 day.

That's the Desktop SKU end-to-end. Web is then a hardening sprint on infra. Hybrid is the sync module + cloud ingest endpoint, which builds on Desktop without touching its core.
