# Lumora POS — Windows Desktop Build (Electron)

> **Status:** v1 plan — Windows-only, single-tenant per install, no auto-update.
> **Supersedes:** every `documentation/step66_*.md` file and the
> `frontend/setup-electron.{bat,sh}` scripts. Those are deleted as part of this work.
> **See also:**
> - `docs/three-sku-delivery-strategy.md` — umbrella plan that places this build
>   into the wider Web / Desktop / Hybrid SKU strategy. This document is the v1
>   single-machine Desktop build; multi-terminal LAN is the planned v2
>   extension (see § 8).
> - `docs/desktop-code-protection.md` — anti-reverse-engineering layers
>   (ProGuard, bytenode, asar integrity, DPAPI-sealed secrets) that bolt onto
>   the build pipeline in § 4.6.
> - `docs/desktop-hardware-integration.md` — thermal printer + cash drawer +
>   barcode scanner integration via the Electron preload bridge. Adds a few
>   files and native deps but doesn't change the bundling story below.

---

## 1. Why this exists

Lumora POS today is a multi-tier SaaS:

- **Backend** — Spring Boot 3.3.7, Java 17, fat JAR, port 8081.
- **Database** — PostgreSQL. 37 Flyway migrations rely on `uuid-ossp`,
  `gen_random_uuid()`, `JSONB`, and `DO $$ … $$` PL/pgSQL blocks. The schema
  is **not** portable to H2 or SQLite without rewriting every migration.
- **Frontend** — Next.js 14.2.35 App Router with `output: 'standalone'`. The
  middleware (`src/middleware.ts`) issues a per-request CSP nonce; `next/image`
  is used in `CartItemCard.tsx`. Both **require** the Next.js Node server —
  `output: 'export'` is not viable.

The client wants a **single-installer Windows desktop app** that runs entirely
on the cashier's machine: no separate Postgres install, no separate Java
install, no SaaS calls. One double-click, one shortcut, one POS.

This document is the build runbook. Follow the steps in order and the build
will reproduce on a clean machine.

---

## 2. Architecture overview

```
NSIS Installer (.exe)
 └─ C:\Program Files\Lumora POS\
     ├─ Lumora POS.exe                       (electron-builder launcher)
     └─ resources\
         ├─ app.asar                         (compiled main + preload + package.json)
         └─ runtime\                         (extraResources, NOT in asar)
             ├─ jre\                         Eclipse Temurin 17 JRE  (~45 MB)
             ├─ pgsql\                       Postgres 16 binaries-only (~90 MB pruned)
             ├─ backend\
             │   └─ lumora-pos-backend.jar   Spring Boot fat JAR
             └─ web\standalone\              next build output (server.js + .next + public)

Per-user writable (%LOCALAPPDATA%\LumoraPOS\):
 ├─ pgdata\                  Postgres data dir
 ├─ uploads\logos\           tenant logo uploads
 ├─ logs\                    electron.log, backend.log, postgresql-*.log
 └─ config\
     ├─ runtime.json         { backendPort, frontendPort, dbPort, jwtSecret, dbPassword }
     ├─ tenant-seed.json     { tenantName, adminEmail, adminPasswordBcrypt }
     └─ .initialized         marker file (touched after successful first run)
```

Process tree at runtime:

```
Lumora POS.exe (Electron main)
 ├─ pg_ctl-managed postgres.exe         (port 55432, 127.0.0.1 only)
 ├─ java.exe -jar lumora-pos-backend.jar (port 8081 default, 127.0.0.1 only)
 └─ node.exe (Electron's Node, ELECTRON_RUN_AS_NODE=1)
      running standalone\server.js      (port 3000)
```

The BrowserWindow loads `http://127.0.0.1:<frontendPort>/`.

### Key choices

| Decision | Choice | Reason |
|---|---|---|
| OS targets | Windows only (NSIS) | Confirmed scope; macOS/Linux deferred. |
| Tenant model | Single tenant per install | Workspace-slug login is hidden; admin entered at first launch. |
| Auto-update | None for v1 | Manual reinstall. Defer `electron-updater` until v1.1 with a code-signing cert. |
| JRE | **Eclipse Temurin 17 JRE** zip | Permissive license, no JDK bloat. |
| Database | **PostgreSQL 16 binaries-only** zip from EnterpriseDB | Real Postgres — only way to keep the existing 37 Flyway migrations untouched. **Not** Zonky embedded-postgres (test fixture lib, multi-platform binaries we don't need). |
| Frontend output | Keep `output: 'standalone'` | Required by middleware + `next/image`. |

---

## 3. Files

### Files to **create**

| Path | Purpose |
|---|---|
| `frontend/electron/main.ts` | Electron main — orchestrates pg + jvm + next, splash, lifecycle. |
| `frontend/electron/preload.ts` | Exposes `window.lumora.getRuntimeInfo()` to the renderer. |
| `frontend/electron/services/postgres.ts` | initdb + pg_ctl + readiness probe. |
| `frontend/electron/services/backend.ts` | Spawn `java`, tail to `backend.log`, poll `/actuator/health`. |
| `frontend/electron/services/frontend.ts` | Spawn standalone Next server in Electron's bundled Node. |
| `frontend/electron/services/runtimeConfig.ts` | Read/create `runtime.json`, generate JWT secret + DB password, allocate ports. |
| `frontend/electron/services/tenantSeed.ts` | First-run-only writes `tenant-seed.json` after collecting the wizard inputs. |
| `frontend/electron/splash.html` | "Initializing database…" progress UI. |
| `frontend/tsconfig.electron.json` | TS build config for `electron/**/*` → `dist-electron/`. |
| `frontend/build/icon.ico` | App icon (placeholder until brand asset). |
| `frontend/build/installer.nsh` | NSIS hook — Defender exclusion + uninstall data prompt. |
| `frontend/types/lumora.d.ts` | TypeScript ambient declaration for `window.lumora`. |
| `backend/src/main/resources/application-desktop.yml` | Desktop profile — `cookie-secure=false`, `server.address=127.0.0.1`, actuator shutdown enabled. |
| `backend/src/main/java/com/lumora/pos/superadmin/service/DesktopBootstrapRunner.java` | `@Profile("desktop")` `ApplicationRunner` that reads `tenant-seed.json` and delegates to `SuperAdminTenantService.provisionFromSeed(...)` on first run. |
| `backend/src/main/java/com/lumora/pos/superadmin/dto/TenantSeed.java` | Immutable DTO matching the JSON Electron writes: `tenantName`, `adminEmail`, `adminPasswordBcrypt`. |

### Files to **modify**

| Path | Change |
|---|---|
| `frontend/package.json` | Add `"main": "dist-electron/main.js"`, scripts `build:electron` / `electron:dev` / `dist`, devDeps `electron@^31`, `electron-builder@^24`, `get-port@^7`, `electron-log@^5`. Add `build` block (electron-builder config). |
| `frontend/src/services/api.ts` | Read API base URL from `window.lumora?.getRuntimeInfo()?.apiUrl` first; fall back to `process.env.NEXT_PUBLIC_API_URL`. |
| `frontend/src/components/auth/LoginForm.tsx` | When `window.lumora?.isDesktop === true`, set `domain` to a fixed slug (`LOCAL`) and hide the workspace input. |
| `backend/pom.xml` | Assert `<excludeDevtools>true</excludeDevtools>` on `spring-boot-maven-plugin` (default in 3.x; explicit is safer). |
| `backend/src/main/java/com/lumora/pos/superadmin/service/SuperAdminTenantService.java` | Add `provisionFromSeed(TenantSeed)` — creates the single tenant + admin user using the **already-bcrypted** password from the seed file. Must bypass `passwordEncoder.encode(...)` so we don't double-hash. Existing `createTenant(CreateTenantRequest)` (line ~149) is the reference for tenant + role wiring. |

### Files to **delete**

```
documentation/step66_INDEX.md
documentation/step66_README.md
documentation/step66_START_HERE.md
documentation/step66_SUMMARY.md
documentation/step66_electron_deployment.md
documentation/step66_electron_desktop_app.md
documentation/step66_electron_patterns_reference.md
documentation/step66_electron_quick_start.md
documentation/step66_visual_guide.md
frontend/setup-electron.bat
frontend/setup-electron.sh
```

---

## 4. Step-by-step build

### 4.1 Stage runtime binaries (one-time, outside git)

Create `D:\Lumora\POS System\runtime\` (gitignored). Place into it:

- `runtime/jre/` — extracted Temurin 17 JRE Windows x64 zip from
  https://adoptium.net (`OpenJDK17U-jre_x64_windows_hotspot_*.zip`).
- `runtime/pgsql/` — extracted Postgres 16 "binaries-only" Windows zip from
  https://www.enterprisedb.com/download-postgresql-binaries.

Prune Postgres to ~90 MB:

```bat
rmdir /S /Q runtime\pgsql\doc
rmdir /S /Q runtime\pgsql\include
rmdir /S /Q runtime\pgsql\pgAdmin*
rmdir /S /Q runtime\pgsql\StackBuilder
rmdir /S /Q runtime\pgsql\symbols
:: keep only English locales
for /D %d in (runtime\pgsql\share\locale\*) do (
  if /I not "%~nxd"=="en" rmdir /S /Q "%d"
)
```

Add `runtime/` to the root `.gitignore`.

### 4.2 Backend changes

**Add** `backend/src/main/resources/application-desktop.yml`:

```yaml
server:
  address: 127.0.0.1

app:
  security:
    cookie-secure: false
  tenant-seed-file: ${APP_TENANT_SEED_FILE:}

management:
  endpoint:
    shutdown:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,shutdown
```

**Add** `DesktopBootstrapRunner.java`:

```java
@Component
@Profile("desktop")
@RequiredArgsConstructor
@Slf4j
public class DesktopBootstrapRunner implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final SuperAdminTenantService tenantService;

    @Value("${app.tenant-seed-file:}")
    private String seedFilePath;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (seedFilePath == null || seedFilePath.isBlank()) return;
        if (tenantRepository.count() > 0) return;       // already seeded

        Path path = Path.of(seedFilePath);
        if (!Files.exists(path)) return;

        TenantSeed seed = new ObjectMapper().readValue(path.toFile(), TenantSeed.class);
        // seed.adminPasswordBcrypt is already bcrypt-hashed by Electron — never persist plaintext.
        // provisionFromSeed must skip passwordEncoder.encode(...) — see "Files to modify".
        tenantService.provisionFromSeed(seed);
        log.info("Desktop bootstrap: seeded tenant '{}'", seed.tenantName());
    }
}
```

**Verify** in `backend/pom.xml`:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <excludeDevtools>true</excludeDevtools>
    </configuration>
</plugin>
```

### 4.3 Electron skeleton

```bash
cd frontend
npm i -D electron@^31 electron-builder@^24 get-port@^7 electron-log@^5
```

**`frontend/tsconfig.electron.json`:**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "CommonJS",
    "moduleResolution": "Node",
    "outDir": "dist-electron",
    "rootDir": "electron",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "resolveJsonModule": true,
    "sourceMap": true,
    "types": ["node"]
  },
  "include": ["electron/**/*.ts"]
}
```

**`frontend/package.json`** updates:

```jsonc
{
  "main": "dist-electron/main.js",
  "scripts": {
    "build:electron": "tsc -p tsconfig.electron.json",
    "electron:dev": "npm run build && npm run build:electron && electron .",
    "dist": "npm run build && npm run build:electron && electron-builder"
  }
}
```

**`runtimeConfig.ts` responsibilities:**

- Resolve `%LOCALAPPDATA%\LumoraPOS\config\runtime.json`.
- On first call, generate `jwtSecret = crypto.randomBytes(64).toString('hex')`
  and `dbPassword = crypto.randomBytes(32).toString('hex')`.
- Use `get-port` to pick `dbPort` (default 55432), `backendPort` (default
  8081), `frontendPort` (default 3000) — fall back to ephemeral on conflict.
  Persist the chosen ports.

**`postgres.ts` responsibilities:**

- If `pgdata/PG_VERSION` is missing:
  - Write a temp file containing the `dbPassword`.
  - `initdb -D pgdata -U lumora -A scram-sha-256 --pwfile=<tmp> -E UTF8 --locale=C --no-instructions`.
  - Delete the temp file.
- Append to `pgdata/postgresql.auto.conf`:
  ```
  port = 55432
  listen_addresses = '127.0.0.1'
  unix_socket_directories = ''
  logging_collector = on
  log_directory = '<absLogDir>'
  ```
- `pg_ctl -D pgdata -l logs/postgres-startup.log start -w -t 60`.
- On first run only, create the database + extensions:
  ```
  CREATE DATABASE lumora_pos OWNER lumora ENCODING 'UTF8';
  \c lumora_pos
  CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
  CREATE EXTENSION IF NOT EXISTS pgcrypto;
  ```

**`backend.ts` responsibilities:** spawn the JVM with this exact env:

```
SPRING_PROFILES_ACTIVE=prod,desktop
SERVER_PORT=<backendPort>
DATABASE_URL=jdbc:postgresql://127.0.0.1:<dbPort>/lumora_pos
DB_USERNAME=lumora
DB_PASSWORD=<dbPassword>
JWT_SECRET=<jwtSecret>
APP_BASE_URL=http://127.0.0.1:<backendPort>
APP_UPLOAD_DIR=%LOCALAPPDATA%\LumoraPOS\uploads\logos
ALLOWED_ORIGINS=http://127.0.0.1:<frontendPort>
LOGGING_FILE_NAME=%LOCALAPPDATA%\LumoraPOS\logs\backend.log
APP_TENANT_SEED_FILE=%LOCALAPPDATA%\LumoraPOS\config\tenant-seed.json
```

Pipe stdout/stderr to `backend.log`. Poll `GET /actuator/health` every 1 s up
to 90 s before continuing.

**`frontend.ts` responsibilities:** spawn `process.execPath` (Electron) with
`ELECTRON_RUN_AS_NODE=1`, args `[<runtime>/web/standalone/server.js]`, env
`PORT=<frontendPort>`, `HOSTNAME=127.0.0.1`,
`NEXT_PUBLIC_API_URL=http://127.0.0.1:<backendPort>`. Poll `GET /` until 200.

**`main.ts` lifecycle:**

1. `app.whenReady()` → ensure dirs → load runtime config → show splash.
2. Start Postgres (with progress messaging on splash).
3. First-run only: collect tenant info via splash modal → bcrypt the password
   in Electron → write `tenant-seed.json`.
4. Spawn backend → wait for `/actuator/health` UP.
5. Spawn frontend → wait for HTTP 200 on `/`.
6. Open `BrowserWindow` → `loadURL('http://127.0.0.1:<frontendPort>/')` →
   close splash.
7. `before-quit`:
   1. Close BrowserWindow.
   2. SIGTERM frontend Node (kill after 5 s).
   3. `POST /actuator/shutdown` (fall back to SIGTERM after 10 s).
   4. `pg_ctl stop -m fast -w -t 30`.
   5. `app.exit(0)`.

Track all child PIDs in `runtime.json`. On next launch, kill survivors with
`tree-kill` before respawning.

**`preload.ts`:**

```ts
import { contextBridge, ipcRenderer } from "electron";
contextBridge.exposeInMainWorld("lumora", {
  isDesktop: true,
  getRuntimeInfo: () => ipcRenderer.invoke("lumora:get-runtime-info"),
});
```

### 4.4 Frontend changes

**`src/services/api.ts`** — base URL is dynamic when running under Electron:

```ts
async function resolveApiUrl(): Promise<string> {
  const w = typeof window !== "undefined" ? (window as any).lumora : undefined;
  if (w?.getRuntimeInfo) {
    const info = await w.getRuntimeInfo();
    if (info?.apiUrl) return info.apiUrl;
  }
  return process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081";
}
```

Memoize the result and initialize the axios client lazily on the first request.

**`src/components/auth/LoginForm.tsx`** — hide workspace slug in desktop mode:

```tsx
const isDesktop = typeof window !== "undefined" && (window as any).lumora?.isDesktop === true;

// in the form:
{!isDesktop && <FormField name="domain" ... />}
// and default values:
defaultValues: { email: "", password: "", domain: isDesktop ? "LOCAL" : "DEMO" },
```

**`frontend/types/lumora.d.ts`:**

```ts
declare global {
  interface Window {
    lumora?: {
      isDesktop: boolean;
      getRuntimeInfo: () => Promise<{ apiUrl: string }>;
    };
  }
}
export {};
```

### 4.5 electron-builder config

In `frontend/package.json`:

```json
"build": {
  "appId": "com.lumora.pos",
  "productName": "Lumora POS",
  "directories": { "output": "dist", "buildResources": "build" },
  "files": ["dist-electron/**/*", "package.json", "!**/*.map"],
  "extraResources": [
    { "from": "../runtime/jre",     "to": "runtime/jre" },
    { "from": "../runtime/pgsql",   "to": "runtime/pgsql" },
    { "from": "../backend/target/lumora-pos-backend.jar", "to": "runtime/backend/lumora-pos-backend.jar" },
    { "from": ".next/standalone",   "to": "runtime/web/standalone" },
    { "from": ".next/static",       "to": "runtime/web/standalone/.next/static" },
    { "from": "public",             "to": "runtime/web/standalone/public" }
  ],
  "win": {
    "target": ["nsis"],
    "icon": "build/icon.ico",
    "artifactName": "LumoraPOS-Setup-${version}.${ext}"
  },
  "nsis": {
    "oneClick": false,
    "perMachine": true,
    "allowToChangeInstallationDirectory": true,
    "createDesktopShortcut": true,
    "createStartMenuShortcut": true,
    "shortcutName": "Lumora POS",
    "include": "build/installer.nsh"
  }
}
```

`build/installer.nsh` adds:

- Windows Defender exclusion for the install dir and `%LOCALAPPDATA%\LumoraPOS\pgdata`.
- An uninstall-time prompt: "Also delete your sales data at
  `%LOCALAPPDATA%\LumoraPOS`?" — default **No**.

### 4.6 Build pipeline

```bat
:: 1. Backend fat JAR
cd D:\Lumora\POS System\backend
.\mvnw.cmd clean package -DskipTests
copy target\lumora-pos-backend.jar ..\runtime\backend\lumora-pos-backend.jar

:: 2. Frontend + Electron
cd ..\frontend
npm ci
npm run build               :: next build → .next/standalone
npm run build:electron      :: tsc → dist-electron
npm run dist                :: electron-builder → dist/LumoraPOS-Setup-*.exe
```

Final artifact: `frontend/dist/LumoraPOS-Setup-<version>.exe`, ~350–400 MB.

---

## 5. Defensive handling — the things that go wrong

These are the failure modes that have to be handled or the install will fail
on a real client machine. None of them are theoretical.

| Risk | Mitigation |
|---|---|
| Antivirus quarantines `java.exe` / `postgres.exe` | NSIS hook adds Defender exclusion for install dir + `pgdata`. Document AV whitelist requirement in user-facing README. |
| OneDrive / `%APPDATA%` ACL corruption breaks `initdb` | Use `%LOCALAPPDATA%` (non-roaming). |
| Reinstall regenerates JWT secret → every session invalidated | `runtime.json` lives under `%LOCALAPPDATA%\LumoraPOS\config\` and survives reinstalls; uninstaller asks before deleting. |
| Port 5432 already used by another Postgres | Bundled instance defaults to **55432**, never touches 5432. JDBC URL is constructed from `dbPort`. |
| Port 8081 / 3000 conflict | `get-port` falls back to ephemeral, persists, propagates to backend env + frontend runtime config. |
| First-run Flyway slow under AV scan | Backend health-wait timeout 90 s; splash messaging acknowledges it. |
| Spring Boot devtools shipped in fat JAR | `<excludeDevtools>true</excludeDevtools>` asserted in `pom.xml`. |
| Backend bound to 0.0.0.0 leaks to LAN | `application-desktop.yml` sets `server.address: 127.0.0.1`. |
| `cookie-secure: true` breaks login over `http://127.0.0.1` | `application-desktop.yml` sets `app.security.cookie-secure: false`. |
| Orphan child processes after a crash | Track PIDs in `runtime.json`; pre-launch sweep kills survivors with `tree-kill`. |
| `gen_random_uuid()` missing on older Postgres | Ship Postgres 16 (built-in) **and** `CREATE EXTENSION IF NOT EXISTS pgcrypto`. |
| Multiple installers running concurrently | NSIS `RequestExecutionLevel admin` + detect existing install via `HKLM\Software\Lumora POS`. |
| Plaintext admin password in seed file | Electron bcrypts before writing `tenant-seed.json`; backend reads only the hash. |

---

## 6. Logs & diagnostics

| Source | Path |
|---|---|
| Electron main | `%LOCALAPPDATA%\LumoraPOS\logs\electron.log` (rotated by `electron-log`, 10 MB × 5) |
| Spring Boot | `%LOCALAPPDATA%\LumoraPOS\logs\backend.log` (Logback `RollingFileAppender` via `LOGGING_FILE_NAME` env) |
| Postgres | `%LOCALAPPDATA%\LumoraPOS\logs\postgresql-*.log` (`logging_collector = on`) |
| Next.js | merged into `electron.log` (child stdout pipe) |

**Help → Export Diagnostics** (in the app menu) zips the `logs/` directory +
`runtime.json` (with `jwtSecret` and `dbPassword` redacted) to
`%USERPROFILE%\Desktop\lumora-diagnostics-<timestamp>.zip` — the user attaches
this when filing a support ticket.

---

## 7. Verification — clean Windows VM smoke test

A green run on **all 16 steps** is the definition of done for v1.

1. Snapshot a clean Windows 11 x64 VM (no Java, no Postgres, Defender on).
2. Copy `LumoraPOS-Setup-<version>.exe` over. Run **as administrator**.
3. NSIS wizard: keep defaults, finish. Tick "Launch Lumora POS".
4. Splash appears → "Initializing database…" → "Starting backend…" →
   main window opens within **90 s** on first run.
5. First-run wizard: enter business name "Smoke Test Store", admin email,
   admin password. Click Continue.
6. Login form auto-fills the workspace slug; log in with the credentials
   just set.
7. Open shift with $200, ring up a $50 cash sale, close shift counted at
   $250 — variance must read **$0.00**.
8. Run the end-of-day report — sale visible.
9. `%LOCALAPPDATA%\LumoraPOS\logs\` contains all three log files. Grep for
   `ERROR` — none expected.
10. **Reboot the VM.**
11. Re-launch — main window appears in **<15 s** (no `initdb` this time).
12. Yesterday's sale is still in the report.
13. **Help → Export Diagnostics** — confirm zip on Desktop, contains all log
    files, no plaintext secrets.
14. Uninstall via Control Panel → confirm prompt about user data; choose
    **No** (keep data).
15. Reinstall on top → log in with same credentials → previous sales still
    present (proves `runtime.json`'s `jwtSecret` and `pgdata` survived).
16. Final pass: install on a VM that **already** has another Postgres on
    5432 — bundled instance must come up on 55432, the host's existing
    Postgres must be untouched.

---

## 8. Effort estimate & out-of-scope

**Estimated effort:** 3–5 focused days for a green smoke-test build, plus
~1 day of clean-VM hardening.

**Out of scope for v1:**

- macOS / Linux installers (Windows-only confirmed).
- Auto-update via `electron-updater` — manual reinstall for v1.
- Code-signing certificate — SmartScreen warning on first launch is
  acceptable; revisit when a cert is procured.
- Kiosk-mode lockdown of the Electron window.
- Hardware integrations (cash drawer, thermal printer, barcode scanner) —
  covered in `docs/desktop-hardware-integration.md`. They add native deps
  (`serialport`, `@thiagoelg/node-printer`) and a preload bridge but do not
  change the installer / bundling story in this document. Bring that doc in
  during the same v1 sprint — a desktop POS that can't print is not shippable.
- **Multi-terminal LAN** (planned v2 — see `docs/three-sku-delivery-strategy.md`).
  v1 is deliberately single-machine: backend binds to `127.0.0.1` and
  `ALLOWED_ORIGINS` is loopback-only. The v2 upgrade is small but not free:
  `application-desktop.yml` would need `server.address: 0.0.0.0` (or a
  configurable LAN IP), `ALLOWED_ORIGINS` would need to include the LAN host,
  the NSIS installer would need a Windows Firewall inbound rule for the chosen
  port, and a separate "Cashier Terminal" Electron build would need to be
  produced that prompts for the store-server URL on first launch. Keep v1's
  loopback-only posture intact rather than half-opening the door now.
- **Hybrid cloud sync** (SKU 3 in the umbrella strategy) — out of scope here
  by design; layers on top of this build via the `local-sync` profile.
