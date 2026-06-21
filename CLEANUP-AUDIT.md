# Cleanup Audit — Lumora POS Desktop build

Scan date: 2026-06-17. Scope: `D:\Lumora\POS System - Copy (3)` (the active Electron desktop build).
Items are grouped by confidence. Verify the "⚠ verify" items before removing.

**Status (2026-06-17): sections 1–4 EXECUTED** — crash dumps/logs deleted, `setup-electron.sh` +
`build-rest.sh` deleted, `jimp` + `png-to-ico` removed (typecheck green), 5 empty licensing dirs
removed. Sections 5 (relocate private key / scratch notes) and 6 (verify) are left for you to decide.

---

## 1. Safe to delete now — root-level cruft (~4.3 MB)

| File(s) | What it is |
|---|---|
| `hs_err_pid13780.log`, `hs_err_pid18536.log`, `hs_err_pid20196.log`, `hs_err_pid20276.log`, `hs_err_pid3752.log` | JVM fatal-error crash dumps (~0.33 MB) |
| `replay_pid13780.log`, `replay_pid18536.log`, `replay_pid20196.log`, `replay_pid20276.log`, `replay_pid3752.log` | JIT replay dumps (~4 MB) |
| `build.log`, `build-rest.log` | stale build console output |

These are already covered by the new `.gitignore` (`*.log`, `hs_err_pid*.log`, `replay_pid*.log`) so they won't be committed; deleting them just reclaims disk.

---

## 2. Obsolete / redundant scripts

| File | Verdict | Reason |
|---|---|---|
| `pos-frontend/setup-electron.sh` | **Delete** | Obsolete CRA/static-export scaffolding. It writes a *different* `main.ts` (loads `out/index.html`, `output: 'export'`), references `serialport`/`usb` (not in `package.json`), and creates `useElectron.ts` / `ipc.ts` / `electron-builder.config.js` that **do not exist** in the real build. Nothing here matches the current standalone-server + JRE architecture. |
| `build-rest.sh` | **Delete** (redundant) | Bash twin of `build-installer.ps1`, but with a **hardcoded path** `ROOT="D:/Lumora/POS System - Copy (3)"` that breaks the moment the folder is renamed. |
| `build-installer.ps1` | **KEEP** ✅ | This is the good one — path-relative (`$MyInvocation`), does the full jar→stage→web→stage→`electron:tsc`→`electron-builder` pipeline. (Note: this *is* the "automated staging step" — earlier notes that said no such step existed are wrong.) |

---

## 3. Unused npm dependencies — `pos-frontend/package.json`

| Package | Where | Verdict |
|---|---|---|
| `jimp` `^0.22.12` (devDep) | only in `package.json` + lockfile; **zero** imports in `src/`, `electron/`, or any script | **Remove** — one-time icon-conversion tool; the icon is already generated at `build/icon.ico`. Pulls in ~30 `@jimp/*` subpackages, so removing it meaningfully shrinks `node_modules`. |
| `png-to-ico` `^2.1.8` (devDep) | only in `package.json` + lockfile; **zero** usage | **Remove** — same one-time PNG→ICO purpose; icon already exists. |

After removing both: `npm install` to prune the lockfile. If you ever need to regenerate `icon.ico` from a PNG, reinstall them temporarily.

> Verified **in use** (do not remove): all `@radix-ui/*`, `qz-tray`, `recharts`, `cmdk`, `next-themes`, `react-hook-form`, `@hookform/resolvers`, `bcryptjs`, `zustand`, `@tanstack/react-query`, `sonner`, etc.

---

## 4. Orphaned empty directories (left by the license-issuance removal)

Emptied when the cloud-only issuing classes were deleted — safe to remove from disk:

```
pos-backend/src/main/java/com/lumora/pos/licensing/controller/
pos-backend/src/main/java/com/lumora/pos/licensing/dto/
pos-backend/src/main/java/com/lumora/pos/licensing/entity/
pos-backend/src/main/java/com/lumora/pos/licensing/enums/
pos-backend/src/main/java/com/lumora/pos/licensing/repository/
```

(`licensing/service/` and `licensing/config/` are **not** empty — they hold the kept verification path: `LicenseGuard`, `LicenseVerifier`, `MachineFingerprint`, `LicenseProperties`. Keep them.) Git doesn't track empty dirs, so this is disk-only tidiness.

---

## 5. Secrets / scratch notes — relocate, don't ship (NOT "unused", but must not be committed)

| File | Action |
|---|---|
| `license-signing-key.PRIVATE.txt` | **Move out of this folder** to a secure location. It's the Ed25519 **private** signing key — it belongs only on the License Server, never in the desktop repo or installer. Do **not** delete (it's the real key); relocate. Already gitignored. |
| `details.md`, `start.md` | Scratch notes containing **real passwords** (e.g. super-admin / tenant creds). Already gitignored; consider deleting or moving to a private notes location. |

---

## 6. ⚠ Verify before removing (your call — not clearly dead)

| Item | Note |
|---|---|
| `lumora_pos/` (1.2 MB) | SaaS umbrella: `docker-compose.yml`, `infra/`, `.github/workflows`, docs + `.gitmodules`. The **desktop build doesn't use any of it** (no docker-compose at runtime). Its `CLAUDE.md` is a useful architecture reference. Decision: keep as a `docs/` reference, or drop it from the desktop repo. If kept in the monorepo, remove its `.gitmodules`/`.git`. |
| `pc-shop-products.csv` (6.8 KB) | Sample product-import data. Keep if used to test CSV import; otherwise removable. |
| `Store.jpg` (101 KB) | Loose image at root, purpose unclear (sample/store photo). Verify whether anything references it; likely removable. |

---

## 7. Do NOT remove (intentional)

| Item | Why |
|---|---|
| `pos-backend/.../db/migration/V55__license_keys.sql` | Kept on purpose. It creates an unused table, but **deleting it breaks Flyway validation** on already-provisioned desktop DBs. |
| `application-desktop.yml` → `app.license.signing.public-key` | Load-bearing — `LicenseVerifier` reads it to verify license tokens. |
| `electron/keys/`, `pos-frontend/.env` | `.env` pins `NEXT_PUBLIC_API_URL=http://localhost:8081` (the backend port the client bakes at build time). Required. |

---

## 8. Build artifacts (regenerated — gitignored; clean only to reclaim disk)

Not "unused files", but large and reproducible. Safe to delete to reclaim disk; they rebuild via `build-installer.ps1`:
`pos-frontend/{dist, dist-electron, .next, resources/web, resources/backend/*.jar}`, `**/node_modules`, `pos-backend/target`.
(Keep `resources/jre` and `resources/postgres-bin` — those are reused binaries that are *not* rebuilt.)

---

## 9. Recommended deeper follow-up (out of scope of this targeted scan)

This audit covered root cruft, scripts, deps, and known orphans — not an exhaustive per-file orphan
analysis. For that, run:
- Frontend: `npx knip` (unused files/exports/deps) or `npx ts-prune` (unused exports).
- Backend: `./mvnw dependency:analyze` (declared-but-unused / used-but-undeclared Maven deps).

---

### Suggested one-shot cleanup (after review)

```powershell
# 1. crash dumps + stale logs
Remove-Item hs_err_pid*.log, replay_pid*.log, build.log, build-rest.log
# 2. obsolete scripts
Remove-Item pos-frontend\setup-electron.sh, build-rest.sh
# 3. unused devDeps
cd pos-frontend; npm remove jimp png-to-ico; cd ..
# 4. empty licensing dirs
Remove-Item -Recurse pos-backend\src\main\java\com\lumora\pos\licensing\controller,
  pos-backend\src\main\java\com\lumora\pos\licensing\dto,
  pos-backend\src\main\java\com\lumora\pos\licensing\entity,
  pos-backend\src\main\java\com\lumora\pos\licensing\enums,
  pos-backend\src\main\java\com\lumora\pos\licensing\repository
# 5. relocate the private key out of the repo (do NOT delete)
Move-Item license-signing-key.PRIVATE.txt "$env:USERPROFILE\lumora-license-signing-key.PRIVATE.txt"
```
