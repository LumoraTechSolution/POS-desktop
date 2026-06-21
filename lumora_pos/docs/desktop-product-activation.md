# Lumora POS — Desktop Product Activation & Licensing

> **Status:** implemented (backend cloud + desktop guard, Electron activation, super-admin UI). Not yet wired into `main.ts` and not yet run against a live stack.
> **Purpose:** stop a customer from copying/reselling the desktop installer by locking each install to one machine with a vendor-issued, cryptographically-signed license.
> **See also:**
> - `desktop-electron-build.md` — the single-installer Windows build this bolts onto. Activation is a new gate in the `main.ts` lifecycle, before the backend/frontend are spawned.
> - `desktop-code-protection.md` — the asar-integrity / bytenode / ProGuard layers that protect the embedded public key and the client-side checks.

---

## 1. Decisions (locked)

| Question | Choice |
|---|---|
| Activation connectivity | **Online, one-time.** The device contacts the existing Spring Boot cloud once at first launch, then runs fully offline forever. |
| Key binding | **Node-locked.** A key binds to the first machine's hardware fingerprint; it cannot then activate on another machine. |
| Enforcement | **Dual-gate.** Electron (JS) verifies before spawning; the Spring Boot `desktop` profile independently refuses to start without a valid signed license. |
| Signature scheme | **Ed25519 (EdDSA).** Private key cloud-only; public key embedded in both the app and the backend JAR. |
| Revocation | Revoking a key blocks **future** activations only. There is **no remote kill-switch** for an already-activated machine (that needs the heartbeat model — see §9). |

### How this stops resale

| Attempt | Blocked by |
|---|---|
| Copy the `.exe` to another shop | Installer is inert without a key; first launch demands activation before anything spawns. |
| Reuse one key on many machines | Key node-locks to the first machine's fingerprint; the cloud refuses a second fingerprint. |
| Copy `license.lic` to another PC | File is DPAPI-sealed to the original machine **and** the fingerprint inside won't match. |
| Patch out the Electron JS check | The Spring Boot backend independently verifies signature + fingerprint with a **JAR-baked** key and won't boot otherwise. |

**Honest caveat:** the code runs on the customer's hardware, so a determined attacker who patches *both* the JS and the Java layer can defeat any client-side scheme. The `bytenode` + ProGuard + asar-integrity layers raise that bar. The realistic goal is making **commercial resale impractical**, not mathematically impossible.

---

## 2. Architecture

### The two artefacts (don't conflate them)
- **License key** — a human-typeable code (`LUM-XXXXX-XXXXX-XXXXX-XXXXX`) the vendor generates and gives the customer. Proof of purchase. Only its SHA-256 **hash** is stored in the cloud DB.
- **License token** — an Ed25519-signed JWS the cloud returns at activation, bound to the machine fingerprint. The app's permission to run. Sealed on disk as `license.lic`.

### Activation (first run, online — once)
```
Electron computes fingerprint ──▶ POST /api/v1/activation/activate
   { key, fingerprint, machineName }
        │  cloud: validate key (ISSUED + unused) → bind fingerprint → sign token
   ◀── { license: <EdDSA JWS>, edition, customerName, expiresAt }
        │  Electron: verify token w/ embedded key → DPAPI-seal → write license.lic
```

### Verification (every launch, offline, dual-gate)
- **Electron gate** (`ensureActivated()` in `main.ts`, before spawning): unseal → verify signature with embedded public key → recompute fingerprint & compare → check expiry. Fail → show activation window, spawn nothing.
- **Backend gate** (`@Profile("desktop")` `LicenseGuard`, `@PostConstruct`): verify signature with the **JAR-baked** key → recompute fingerprint from the registry itself → check expiry. Fail → throw, aborting context startup so the server never serves.

### Machine fingerprint
`SHA-256("guid:<MachineGuid>")` where MachineGuid is `HKLM\SOFTWARE\Microsoft\Cryptography\MachineGuid`. Chosen because it is per-OS-install and survives dock/NIC/disk swaps (avoids false lockouts); only an OS reinstall changes it, which is exactly when support-mediated re-activation is appropriate. Computed identically in TypeScript (`fingerprint.ts`) and Java (`MachineFingerprint.java`) so the backend can verify independently. Falls back to a host/MAC/CPU composite only if the GUID can't be read.

### License token claims
`iss=lumora-pos`, `iat`, `exp` (absent = perpetual), `kid` (key id), `fp` (bound fingerprint), `customer`, `edition`, `features` (comma-separated), `machine`.

---

## 3. What was built

### Backend — cloud side (`pos-backend`, profile `!desktop`)
| File | Role |
|---|---|
| `db/migration/V50__license_keys.sql` | `license_keys` table. Stores only the key **hash**, plus customer/edition/status/bound fingerprint/expiry. *(Filesystem high-water mark was V49 — CLAUDE.md's "V41" is stale.)* |
| `licensing/entity/LicenseKeyEntity.java`, `enums/LicenseStatus.java`, `repository/LicenseKeyRepository.java` | Persistence. Status: `ISSUED → ACTIVE → REVOKED/EXPIRED`. |
| `licensing/service/LicenseKeyGenerator.java` | Typeable `LUM-…` keys with a check digit; normalize + SHA-256 hash. |
| `licensing/service/LicenseSigningService.java` | Ed25519 signing. Loads `app.license.signing.*`; generates an ephemeral pair + logs it if unset (dev). |
| `licensing/service/ActivationService.java` | issue / activate (node-lock) / revoke / reset-binding. Re-activation from the **same** fingerprint is allowed (reinstalls); a different one is rejected. |
| `licensing/controller/ActivationController.java` | **Public** `POST /api/v1/activation/activate` (permit-all in `SecurityConfig`). |
| `licensing/controller/SuperAdminLicenseController.java` | `POST /api/v1/super-admin/licenses` (issue, shown once), list, `PATCH …/revoke`, `PATCH …/reset-binding`, `GET …/signing-public-key`. |

### Backend — desktop gate (`pos-backend`, profile `desktop`)
| File | Role |
|---|---|
| `licensing/service/LicenseVerifier.java` | Ed25519 verify with the **JAR-baked** public key. Fails closed if the key is blank/placeholder. |
| `licensing/service/MachineFingerprint.java` | Java mirror of `fingerprint.ts`; recomputes the fingerprint independently. |
| `licensing/service/LicenseGuard.java` | `@PostConstruct` enforcement — throws to abort startup if the license is missing/invalid/wrong-machine/expired. |
| `resources/application-desktop.yml` | Desktop profile (loopback, cookie-secure off, actuator shutdown) + `app.license.*` with the baked public-key placeholder. |

`LicenseProperties` gained `token` + `machineFingerprint`. The cloud beans are `@Profile("!desktop")` so the till never runs the signer/issuer.

### Electron (`pos-frontend/electron`)
| File | Role |
|---|---|
| `services/fingerprint.ts` | MachineGuid-based fingerprint + short device code for support calls. |
| `services/license-crypto.ts` | Dependency-free Ed25519 JWS verification (Node `crypto`). Rejects non-EdDSA algs (alg-confusion defense). |
| `services/license.ts` | `activate()` (HTTP → verify → DPAPI-seal via `safeStorage` → write `license.lic`) and `verifyLocalLicense()` (offline, fail-closed). |
| `keys/license-public-key.ts` | Embedded public-key constant + `isPublicKeyConfigured()` guard (placeholder until baked). |
| `activation-window.ts` | `ensureActivated()` gate + the activation `BrowserWindow` + IPC. |
| `activation-preload.ts`, `activation.html` | Activation screen (key input with live formatting, device code, errors). |

### Super-admin UI (`pos-frontend/src`)
| File | Role |
|---|---|
| `types/license.ts`, `services/superAdminLicenseService.ts` | Types + API client. |
| `app/(super-admin)/super-admin/licenses/page.tsx` | Key list: status filter, pagination, Release (reset-binding) + Revoke actions. |
| `app/(super-admin)/super-admin/licenses/IssueLicenseModal.tsx` | Issue form → **show-once** key view with copy button. |
| `app/(super-admin)/layout.tsx` | Added "Desktop Licenses" nav item. |
| `tsconfig.json` | Excludes `electron/` (compiled separately via `tsconfig.electron.json`). |

---

## 4. Integration — wiring activation into `main.ts`

This is the remaining glue (part of the `desktop-electron-build.md` work). In the Electron `main.ts` lifecycle, **after** runtime config and **before** spawning the backend/frontend:

```ts
import { ensureActivated } from "./activation-window";

// CLOUD_ACTIVATION_URL is the public SaaS origin, e.g. https://app.lumora.com
const license = await ensureActivated(CLOUD_ACTIVATION_URL);
// license.token → pass to the backend as APP_LICENSE_TOKEN
// license.info  → { customer, edition, features }
```

If `ensureActivated` rejects (user closed the window without activating), quit — the POS must not run unlicensed.

Then in `electron/services/backend.ts`, add to the JVM env:

```
SPRING_PROFILES_ACTIVE=prod,desktop
APP_LICENSE_TOKEN=<license.token>
APP_MACHINE_FINGERPRINT=<computeFingerprint()>   # fallback only; backend recomputes its own
```

### Baking the public key (build step)
1. Get the key once from the cloud: `GET /api/v1/super-admin/licenses/signing-public-key` (super-admin auth).
2. Paste the **same** base64 value into **both**:
   - `pos-frontend/electron/keys/license-public-key.ts` → `EMBEDDED_PUBLIC_KEY`
   - `pos-backend/src/main/resources/application-desktop.yml` → `app.license.signing.public-key`
3. The backend value must **not** be overridable by the launcher — its independence is the whole point of the backend gate.

The cloud must have a stable key pair set via `app.license.signing.private-key` / `.public-key` (env/secret manager) — not the dev ephemeral pair.

---

## 5. Build pipeline additions

On top of `desktop-electron-build.md` §4.6:
- Ensure the cloud backend is deployed with a **stable** `app.license.signing.*` key pair.
- Bake the public key into the two files above before `npm run build` / `npm run dist`.
- `tsconfig.electron.json` already covers `electron/**/*.ts`; ensure the build copies `electron/activation.html` into `dist-electron/` alongside the compiled JS.
- No extra runtime deps: sealing uses Electron's built-in `safeStorage`; verification uses Node's built-in `crypto`.

---

## 6. Operations runbook

- **Issue a key:** Super-Admin → Desktop Licenses → *Issue License Key*. Choose customer/edition/validity/features. The full key is shown **once** — copy and deliver it with the installer.
- **Customer gets a new computer:** their key won't auto-activate on new hardware (by design). Find the key → *Release* (reset-binding) → they re-activate on the new machine.
- **Stop a reseller / non-payer:** *Revoke* the key. Blocks future activations. (Does not kill an already-activated machine — see §9.)
- **Support diagnostics:** the activation screen shows a short **device code** (first bytes of the fingerprint) the customer can read out.

---

## 7. Verification status

Verified here without a full toolchain:
- Backend licensing package (cloud + desktop guard): **`javac` type-check clean** against the real Spring/JJWT/Lombok jars.
- Electron crypto + fingerprint: **Node round-trip test, 7/7** — a JWS built exactly like jjwt's `Jwts.SIG.EdDSA` (alg=EdDSA, raw 64-byte signature) verifies; tamper / wrong-key / malformed / non-EdDSA-alg all rejected; fingerprint computes on real hardware.
- Super-admin UI: **`tsc --noEmit` clean** in the real Next.js project.

**Not yet done** (needs Maven + Electron + a live stack):
1. `mvn clean verify` — confirm V50 applies to Postgres and the context boots.
2. Issue a key in the UI and exercise the show-once + activate flow end-to-end.
3. Wire `ensureActivated()` into `main.ts`; run the clean-VM activation smoke test.

---

## 8. Threat-model notes
- **Alg-confusion:** the Electron verifier and `LicenseVerifier` only accept `EdDSA`; an attacker can't downgrade to HS256 and abuse the public key as an HMAC secret.
- **Key independence:** the backend's public key is baked into the JAR, never accepted from the launcher — so a patched launcher can't substitute its own key + self-signed token.
- **Fail-closed:** missing/placeholder key, unreadable seal, bad signature, wrong machine, expired → all block startup.
- **Private key:** lives only in the cloud (secret manager / env). Never shipped.

## 9. Out of scope / upgrade paths
- **Remote kill-switch** for activated machines — requires the "online + periodic heartbeat" model (a heartbeat endpoint + an offline grace window so a flaky connection doesn't lock the till). Additive to this design.
- **Per-business multi-terminal** licensing (N machines per key) — ties into the v2 multi-terminal LAN work in `desktop-electron-build.md` §8.
- **Self-service re-activation** (N free machine moves before support is required) — the `max_activations` column exists; the policy isn't wired yet.
