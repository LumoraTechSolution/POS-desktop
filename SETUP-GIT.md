# GitHub repo setup — Lumora POS Desktop

How to put this desktop build into a single GitHub repository, safely.

---

## 1. Repo strategy

**One repo for the desktop build.** `pos-backend`, `pos-frontend`, and the `electron/` launcher are
tightly coupled — they version and ship together as a single installer, share the baked-in port
invariant (backend 8081), the same Ed25519 public key (in both `application-desktop.yml` and
`electron/keys`), and the activation contract. A monorepo makes the staging step (copying the jar +
web bundle into `pos-frontend/resources`) trivial and lets one commit cover a backend+frontend+electron
change atomically.

**Keep these OUT — important:**
- **The License Server stays in its own separate repo.** `D:\Lumora\Lumora License service` holds the
  Ed25519 **private** signing key and deploys independently to Vercel. It must never live in the same
  repo as the client you ship to customers — that's a security boundary, not just organization.
- **`lumora_pos/`** is the SaaS umbrella (docker-compose, infra, submodule wiring) and isn't used by the
  desktop runtime. Either keep it as a `docs/` reference (after removing its `.git`/`.gitmodules`) or
  drop it from the desktop repo. Your call — not required for the build.

---

## 2. `.gitignore` (already in place)

A root `.gitignore` was added covering:
- **Secrets** — `license-signing-key.PRIVATE.txt`, `*.PRIVATE.txt`, `**/db.properties`, `*.pem/.p12/.pfx/.keystore`, `**/.env.local`, and the credential-bearing scratch notes `details.md` / `start.md`.
- **Bundled binaries / installer output** — `pos-frontend/resources/`, `pos-frontend/dist/`, `pos-frontend/dist-electron/`, `*.exe`, `*.blockmap`.
- **Deps / build output** — `**/node_modules/`, `**/.next/`, `**/target/`, coverage, test artifacts.
- **Logs / crash dumps** — `*.log`, `hs_err_pid*.log`, `replay_pid*.log`.
- **Editor/OS** + `.claude/`.

`pos-frontend/.gitignore` was also fixed: the old `/build` rule was removed (it was hiding the NSIS
installer **source** — `installer.nsh`, `install-postgres.ps1`, `icon.*`), and `dist/`, `dist-electron/`,
`resources/` were added.

> A parent `.gitignore` can't re-include what a nested one excludes — that's why the `pos-frontend`
> fix had to be made in that file, not just the root.

---

## 3. Git init plan

Run **after** renaming the folder to `D:\Lumora\POS SYSTEM DESKTOP`, from a fresh terminal in that
folder (PowerShell). The folder rename can't be done while a Claude session/IDE holds it open.

### Step 1 — Flatten the nested repos
`pos-backend`, `pos-frontend`, and `lumora_pos` each currently have their **own** `.git`. For a single
repo you must remove them (this discards their separate local history — back up first if you care):

```powershell
# optional history backup:
Compress-Archive pos-backend\.git, pos-frontend\.git, lumora_pos\.git ..\nested-git-backup.zip
# flatten:
Remove-Item -Recurse -Force pos-backend\.git, pos-frontend\.git, lumora_pos\.git
Remove-Item -Force lumora_pos\.gitmodules -ErrorAction SilentlyContinue
```

### Step 2 — Init + stage
```powershell
git init
git branch -M main
git add -A
```

### Step 3 — VERIFY before committing (critical safety gate)
Each command must return **nothing**:
```powershell
git ls-files | Select-String -Pattern "PRIVATE|db.properties|\.exe$|resources/|/dist/|node_modules|target/|details.md|start.md"
git count-objects -vH    # tracked size should be KB/MB, not hundreds of MB
```
If anything sensitive shows up: fix `.gitignore`, `git rm --cached <path>`, re-check.

### Step 4 — Commit + push
```powershell
git commit -m "Initial commit: Lumora POS desktop (Electron) build"
gh repo create lumora-pos-desktop --private --source . --remote origin --push
```
(or add the remote manually: `git remote add origin <url>` then `git push -u origin main`.)

---

## 4. After cloning fresh

A clean clone will **not** contain the gitignored runtime pieces. To build an installer you need to
repopulate `resources/`:
- `resources/jre` and `resources/postgres-bin` — reused binaries; copy them in from a prior build (they are not rebuilt).
- `resources/backend/pos-backend.jar` and `resources/web` — produced by the staging build (`build-installer.ps1`, or the manual pipeline in `CLAUDE.md`).

---

## 5. Reminders

- **Never commit** `license-signing-key.PRIVATE.txt` — move it out of the repo to a secure location.
- The **License Server** is a different repo entirely.
- `build-installer.ps1`'s `mvnw.cmd` step needs a path **without parentheses** — works once the folder
  is renamed to `POS SYSTEM DESKTOP`.
- See `CLEANUP-AUDIT.md` for files worth deleting before the first commit, and `CLAUDE.md` for the
  desktop build architecture and commands.
