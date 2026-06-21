# Lumora POS — Desktop Code Protection (Anti-Reverse-Engineering)

> **Status:** v1 plan — pragmatic, layered. No silver bullet exists for this.
> **See also:** `docs/desktop-electron-build.md` (the build this layers on top of),
> `docs/three-sku-delivery-strategy.md` (umbrella SKU plan).
> **Scope:** Windows desktop installer only. Web SKU is unaffected (server code
> stays on our cloud).

---

## 1. The honest baseline — what you cannot achieve

Read this section before any work starts. The team will waste effort if it expects more than this delivers.

- **Code that runs must be decryptable by the machine running it.** Any encryption key has to live somewhere in the installed binary. A determined attacker with a debugger and a few days will recover it.
- **The Java backend is the easiest layer to crack.** A `.jar` is a `.zip` of `.class` files. CFR, Procyon, or JD-GUI decompile readable Java in seconds.
- **The Electron renderer (Next.js) is also easy.** `app.asar` is unpacked with `npx asar extract`. Webpack-minified JS reverses to readable code with `prettier` + a few hours.
- **Code signing does not prevent reverse engineering.** It proves the binary came from us. It does not encrypt anything.
- **Anti-debug tricks fail to professionals.** They slow down a curious developer for an hour and stop nobody who does this for a living.

**What we actually achieve with the stack below:** raise the effort from "30-minute lunch break" to "multi-day, multi-skill, easy-to-detect-by-source-divergence." That is enough to deter casual cloning and to give legal/contractual remedies real teeth. Treat protection as **friction**, not as security. The actual security boundary is the **commercial license + customer relationship**.

---

## 2. Threat model

| Threat actor | Motivation | What we want to prevent |
|---|---|---|
| **Curious customer admin** | Edit prices / unlock features / bypass plan limits | Casual tampering with bundled JS or JAR; reading DB credentials from disk |
| **Competing local POS vendor** | Copy domain logic (variance math, tax rules, sale flow) into their product | Direct lift of business logic; rebadging |
| **Disgruntled ex-employee / contractor** | Build a fork to sell on the side | Same as above, but they may have prior context — friction matters less |
| **Skilled reverse-engineer for hire** | Targeted IP extraction | We accept this is a losing battle; mitigate via legal / signing / detection, not crypto |

**Highest-value IP to protect, in order:**
1. **Java backend** (`backend/...`) — money math (`CashSessionService` variance, `SaleService` stock+tax flow, `PurchaseOrderService` receiving), tenant configuration logic, JWT issuance.
2. **Electron main process** (`electron/main.ts`, `electron/services/*`) — license checks, integrity checks, machine-binding logic. If the attacker patches this, the rest of the protection collapses.
3. **Per-install secrets** — JWT secret, DB password in `runtime.json`. Compromise lets an attacker forge tokens or read the customer's data.
4. **Next.js renderer bundle** — lowest priority. The same code is publicly served to browsers in the Web SKU. Don't spend much here.

---

## 3. The protection stack

Apply layers in order. Each layer is independently useful — ship layer 1 even if layer 6 is months away.

### Layer 1 — Build hygiene (free, do first)

These changes cost almost nothing and remove the lowest-hanging fruit. **Do not skip these to chase fancier layers.**

- Strip source maps from production. Add `productionBrowserSourceMaps: false` to `next.config.mjs`. Add `"!**/*.map"` to `electron-builder` `files` (already in the build doc — verify).
- Strip Spring Boot devtools — `<excludeDevtools>true</excludeDevtools>` (already in the build doc).
- Disable Spring Boot's `spring-boot-actuator` `env`, `beans`, `mappings`, `configprops` endpoints in `application-desktop.yml`. The build doc currently exposes only `health,info,metrics,prometheus,shutdown` which is correct — **do not loosen this**.
- Strip Java debug symbols at compile: `<compilerArgs><arg>-g:none</arg></compilerArgs>` on `maven-compiler-plugin` in `backend/pom.xml`. Trade-off: stack traces lose line numbers in prod logs. Mitigation: keep a debug-symbol JAR in private artifact storage for incident triage; ship the stripped one.
- Remove all `console.log` / `System.out.println` from production paths via lint rules. Logged data is the easiest source of business-logic insight.
- `.gitignore` the `runtime/` staging directory (already in build doc).
- Remove all comments referencing internal tickets, customer names, or strategic decisions from shipped source. They survive obfuscation and tell a reverse-engineer where the interesting code lives.

### Layer 2 — Java backend obfuscation with ProGuard (1–2 days)

ProGuard is free, mature, and the standard answer for Java obfuscation. It renames classes/methods/fields to `a`, `b`, `c`, removes unused code, and (with `-overloadaggressively` + `-repackageclasses`) collapses the package tree into a single namespace.

**The catch with Spring:** Spring discovers beans, controllers, and JPA entities by reflection on class names + annotations. Naive ProGuard breaks the app on first boot. Required keep rules:

- Keep all `@Entity`, `@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`, `@Configuration` classes and their declared methods.
- Keep all `@SpringBootApplication`, `@EnableXxx`, and `@Bean` factory methods.
- Keep `application*.yml` config keys (they're matched by string).
- Keep Jackson DTOs (request/response bodies serialised by name).
- Keep JPA repository interfaces; their proxies are generated by name.
- Keep Lombok-generated methods if Lombok runs before ProGuard (it should — Lombok is compile-time).
- Preserve `@Override` chains so Spring AOP doesn't lose virtual dispatch.

**Acceptable obfuscation surface:** internal services, helpers, mappers, non-DTO POJOs, the `common/`, `cashsession/`, `sales/` domain logic that isn't exposed via Spring scanning.

**Build integration:**

```xml
<!-- backend/pom.xml — additional plugin for the local profile only -->
<profile>
  <id>local</id>
  <build>
    <plugins>
      <plugin>
        <groupId>com.github.wvengen</groupId>
        <artifactId>proguard-maven-plugin</artifactId>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>proguard</goal></goals>
          </execution>
        </executions>
        <configuration>
          <obfuscate>true</obfuscate>
          <proguardInclude>${project.basedir}/proguard.conf</proguardInclude>
          <inFilter>com/lumora/pos/**</inFilter>
          <libs>
            <lib>${java.home}/jmods/java.base.jmod</lib>
            <!-- ... full module list ... -->
          </libs>
          <attach>false</attach>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

`backend/proguard.conf` — minimum keep rules:

```
-dontoptimize                       # Spring AOP + JPA do not survive aggressive optimization
-dontwarn
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Spring Boot entry point
-keep public class com.lumora.pos.LumoraPosApplication { public static void main(java.lang.String[]); }

# Anything Spring scans — keep class names + public/protected members
-keep @org.springframework.stereotype.Component class * { public protected *; }
-keep @org.springframework.stereotype.Service   class * { public protected *; }
-keep @org.springframework.stereotype.Repository class * { public protected *; }
-keep @org.springframework.stereotype.Controller class * { public protected *; }
-keep @org.springframework.web.bind.annotation.RestController class * { public protected *; }
-keep @org.springframework.context.annotation.Configuration class * { public protected *; }

# JPA entities — Hibernate touches every field reflectively
-keep @jakarta.persistence.Entity class * { *; }
-keep @jakarta.persistence.MappedSuperclass class * { *; }
-keep @jakarta.persistence.Embeddable class * { *; }

# Spring Data repository interfaces
-keep interface org.springframework.data.repository.Repository
-keep interface * extends org.springframework.data.repository.Repository { *; }

# Jackson — DTOs serialised by field name
-keep class com.lumora.pos.**.dto.** { *; }
-keep class com.lumora.pos.**.request.** { *; }
-keep class com.lumora.pos.**.response.** { *; }

# Lombok-generated methods (preserve getters/setters Spring binds to)
-keepclassmembers class com.lumora.pos.** {
    public *** get*();
    public *** is*();
    public void set*(***);
}

# Strip log statements and string-encrypt the rest
-assumenosideeffects class org.slf4j.Logger {
    public void trace(...);
    public void debug(...);
}
-adaptresourcefilenames    **.properties,**.xml,**.yml
-adaptresourcefilecontents **.properties,META-INF/MANIFEST.MF
```

**Verification:** every integration test in `src/test/java/...` must still pass against the obfuscated JAR. Add a CI step that runs `./mvnw -P local verify` and asserts the obfuscated JAR boots, runs Flyway, and completes a sale + cash-session-close.

**Trade-off accepted:** stack traces in `backend.log` show obfuscated names. Keep the ProGuard `mapping.txt` (output by every build) checked into private artifact storage and use ProGuard's `retrace` tool when triaging customer incidents.

### Layer 3 — Bytenode compilation of Electron main + preload (~1 day)

The Electron main process holds every check that, if patched, defeats the rest of this stack: integrity verification, license validation, machine binding, secret unsealing. Compile it from JS to V8 bytecode (`.jsc`) so a casual attacker sees opaque bytecode instead of pretty TypeScript.

```bash
npm i -D bytenode
```

After `tsc -p tsconfig.electron.json` produces `dist-electron/*.js`:

```js
// frontend/scripts/compile-electron.js
const bytenode = require('bytenode');
const fs = require('fs');
const path = require('path');

const targets = [
  'dist-electron/main.js',
  'dist-electron/preload.js',
  'dist-electron/services/runtimeConfig.js',
  'dist-electron/services/postgres.js',
  'dist-electron/services/backend.js',
];
for (const file of targets) {
  bytenode.compileFile({ filename: file, output: file.replace(/\.js$/, '.jsc') });
  fs.unlinkSync(file);   // remove the JS source
  fs.writeFileSync(file, `require('bytenode'); require('./${path.basename(file).replace(/\.js$/, '.jsc')}');`);
}
```

Wire it into `frontend/package.json`:

```jsonc
"scripts": {
  "build:electron": "tsc -p tsconfig.electron.json && node scripts/compile-electron.js"
}
```

**Caveats:**

- Bytenode output is tied to the **exact Node/V8 version Electron embeds**. A bytenode file compiled for Electron 31 will not load in Electron 32. Pin Electron precisely (no `^`).
- `frontend.ts` uses `ELECTRON_RUN_AS_NODE=1` to run Next's `server.js` in Electron's bundled Node. That Node version must match the bytenode compile, otherwise `server.js` (which we are **not** compiling — it's third-party) is fine but anything we import into it won't be.
- Source maps must be off for bytenode targets (`"sourceMap": false` for the electron tsconfig in production builds).

**What we deliberately do not bytenode-compile:**

- The Next.js renderer bundle (priority 4 — low value, big breakage risk).
- The Spring Boot JAR (different toolchain — that's Layer 2).

### Layer 4 — asar integrity + asar packing (free, ~2 hours)

Electron 30+ supports asar integrity checks. The launcher hashes `app.asar` at build time and refuses to start if the hash differs at runtime. This catches the trivial "unzip the asar, edit a file, rezip" attack.

In `frontend/package.json` build config:

```jsonc
"build": {
  "asar": true,
  "asarUnpack": [
    "**/*.node"
  ],
  "electronLanguages": ["en-US"],
  "win": {
    "target": ["nsis"],
    "asarIntegrity": true
  }
}
```

Verify in a CI check that `asarIntegrity` is `true` and that the resulting `Lumora POS.exe` contains the integrity hash (electron-builder logs it).

### Layer 5 — Machine-bind the per-install secrets (DPAPI, ~1 day)

The build doc currently writes `runtime.json` containing `jwtSecret` and `dbPassword` to `%LOCALAPPDATA%\LumoraPOS\config\` in plaintext. Anyone with read access to the user profile can lift them and:

- Forge JWTs that the backend will accept.
- Connect to the Postgres instance as `lumora` and dump every sale.

**Fix:** wrap secret values with **Windows DPAPI** (`CryptProtectData` / `CryptUnprotectData`) using the **current user scope**. The OS encrypts under a key derived from the user's credentials; the file is unreadable by other users on the same machine and unreadable on a different machine even by the same Windows username.

```ts
// frontend/electron/services/runtimeConfig.ts (sketch)
import { safeStorage } from 'electron';

interface SealedRuntimeConfig {
  backendPort: number;
  frontendPort: number;
  dbPort: number;
  jwtSecretSealed: string;        // base64(DPAPI ciphertext)
  dbPasswordSealed: string;
}

function seal(plain: string): string {
  if (!safeStorage.isEncryptionAvailable()) {
    throw new Error('safeStorage unavailable; install corrupt or running unsupported OS');
  }
  return safeStorage.encryptString(plain).toString('base64');
}

function unseal(sealed: string): string {
  return safeStorage.decryptString(Buffer.from(sealed, 'base64'));
}
```

Electron's `safeStorage` already uses DPAPI under the hood on Windows. The unsealed secrets are passed to the JVM child process via env vars (`JWT_SECRET`, `DB_PASSWORD`) — they exist in process memory but never on disk in plaintext.

**Side benefit:** copying `runtime.json` to another machine no longer transfers the credentials. The customer can't accidentally ship their own JWT signing key to a competitor by sharing a backup.

**Trade-off:** if the user account is destroyed and recreated with the same username (e.g. profile rebuild), the secrets become unrecoverable. Document this in the support runbook: a profile rebuild requires a fresh install.

### Layer 6 — Code signing (when an EV cert is procured)

Out of scope for v1 of the build doc but listed here so it is not forgotten:

- An **EV code-signing certificate** removes the Windows SmartScreen warning on first launch and chains every binary to a verifiable publisher.
- It does not encrypt anything. It establishes that any tampered build cannot impersonate Lumora.
- Sign **both** `Lumora POS.exe` (Electron launcher) **and** the bundled `lumora-pos-backend.jar` (via `jarsigner`). Unsigned JARs allow a swap attack: replace our JAR with a malicious one inside the install dir.
- electron-builder integrates code signing via `signtool.exe`; the cert lives in a hardware token (EV requirement). Document the signing workflow as a separate runbook when the cert arrives.

Until then, ship unsigned with documented SmartScreen guidance — acceptable per build doc § 8.

### Layer 7 (future, not v1) — GraalVM native-image for the backend

The strongest single technique on the table. GraalVM compiles the Spring Boot app ahead-of-time to a native Windows `.exe`. There are **no `.class` files in the binary** for an attacker to decompile — the JVM itself is gone. Output is a single ~80 MB native binary (smaller than JRE + fat JAR combined).

**Not v1 because:**

- Spring Native (now `spring-boot-starter-parent` AOT mode) requires reflection hints for every reflective call (Hibernate, Jackson, Spring proxies). For a 37-migration codebase with the full Spring Data + JPA stack, this is a multi-week integration effort with real risk of subtle behavioural changes.
- Some libraries Lumora uses (Sentry, OpenTelemetry, Logstash encoder) have partial or no native-image support.
- Build times go from 30 s to 5–10 minutes.

**Track this as a v2 item.** When attempted, gate it behind the existing integration test suite — if any test fails on the native image that passes on the JVM build, do not ship.

---

## 4. What NOT to do (anti-patterns)

These come up in every "protect our app" thread. They cost engineering time and deliver near-zero protection.

- **Encrypting the JAR or asar with a "secret key in the launcher."** The launcher must contain the key to decrypt at runtime. Anyone who runs `strings` on the launcher recovers the key in seconds. This is theatre, not protection.
- **Custom JNI loaders that decrypt classes at runtime.** Marginal extra friction over ProGuard, hours of complexity, breaks every JVM upgrade.
- **Anti-debugger checks (`IsDebuggerPresent`, JVM agent detection).** Bypassed in 30 seconds with a debugger that hides itself. Adds support burden when legitimate debuggers (e.g., Process Explorer, antivirus) trip the check.
- **"DRM" libraries with online activation** in an offline-first POS. The product is sold on the promise of working without internet. Online activation breaks that promise on day one of a customer outage.
- **Stripping all logs, including ERROR.** You will lose the ability to support paying customers. Strip TRACE/DEBUG; keep INFO+.
- **Encrypting the Postgres data directory at the application level.** OS-level disk encryption (BitLocker) is the right answer. Application-level encryption inside the DB requires Postgres TDE (enterprise-only) and breaks pg_dump backups.
- **Bytenode-compiling the Next.js standalone server.** It's a third-party `server.js` we don't control; bytenode-compiling third-party code that imports other third-party code is a debugging black hole.

---

## 5. Build pipeline integration

These steps slot into `docs/desktop-electron-build.md` § 4.6. Replace the existing build sequence with:

```bat
:: 1. Backend fat JAR → ProGuarded
cd D:\Lumora\POS System\backend
.\mvnw.cmd -P local clean package -DskipTests
:: ProGuard runs in the package phase; output is target/lumora-pos-backend.jar
:: target/proguard/mapping.txt is the deobfuscation map — archive privately
copy target\lumora-pos-backend.jar ..\runtime\backend\lumora-pos-backend.jar
copy target\proguard\mapping.txt ..\artifacts\mapping-%VERSION%.txt

:: 2. Sign the JAR (when EV cert is available — Layer 6)
:: jarsigner -keystore lumora.pfx -tsa http://timestamp.digicert.com ..\runtime\backend\lumora-pos-backend.jar lumora

:: 3. Frontend + Electron, with bytenode compilation
cd ..\frontend
npm ci
npm run build               :: next build → .next/standalone
npm run build:electron      :: tsc + bytenode → dist-electron/*.jsc
npm run dist                :: electron-builder → dist/LumoraPOS-Setup-*.exe
:: asar integrity hash visible in electron-builder log
```

**Files to add to the build doc's "Files to create" list:**

- `backend/proguard.conf` — keep rules from § 3, Layer 2.
- `frontend/scripts/compile-electron.js` — bytenode wrapper from § 3, Layer 3.

**Files to modify in the build doc:**

- `backend/pom.xml` — add ProGuard plugin under the `local` Maven profile + strip debug symbols (`-g:none`).
- `frontend/package.json` — pin Electron exactly (drop the `^`), add `bytenode` devDep, update `build:electron` script, add `asarIntegrity: true` to the build config.
- `frontend/electron/services/runtimeConfig.ts` — wrap secrets with `safeStorage` (DPAPI).
- `frontend/next.config.mjs` — `productionBrowserSourceMaps: false` (already implicit; make explicit).

**`artifacts/` directory** (gitignored, like `runtime/`) — holds `mapping-<version>.txt` and any signing metadata. Back this up; without the mapping file an obfuscated stack trace is unreadable.

---

## 6. Verification — protection is actually doing something

Treat each layer as a green/red check. Run these on every release candidate.

| Layer | Verification step | Expected result |
|---|---|---|
| 1 — Hygiene | `unzip -l Lumora\ POS-Setup-<v>.exe` and grep for `*.map`, `*.java`, `application-dev.yml`, devtools classes | None present |
| 2 — ProGuard | `jar tf lumora-pos-backend.jar \| grep com/lumora/pos \| head` | Class names should be `a.class`, `b.class`, etc. — not human-readable |
| 2 — ProGuard | `javap -p` against an obfuscated class | Methods named `a`, `b`; private fields obfuscated; public Spring-touched methods preserved |
| 2 — ProGuard | Run `./mvnw -P local verify` | All integration tests pass against the obfuscated JAR |
| 3 — Bytenode | `cat dist-electron/main.js` | A two-line `require('bytenode')` shim. The actual logic lives in `main.jsc` (binary) |
| 3 — Bytenode | `file dist-electron/main.jsc` | "data" — not text |
| 4 — asar integrity | Edit a file inside `app.asar` post-install, restart app | App refuses to launch with an integrity error |
| 4 — asar integrity | `npx asar list app.asar` | Lists files (asar is intentionally not encrypted); confirms packing happened |
| 5 — DPAPI | Open `runtime.json` in Notepad | `jwtSecretSealed` and `dbPasswordSealed` are base64 blobs, not the secrets |
| 5 — DPAPI | Copy `runtime.json` to a second VM under the same Windows username, launch | App fails to unseal — proves machine binding |
| 6 — Signing | `signtool verify /pa /v "Lumora POS.exe"` | "Successfully verified" with the Lumora cert chain |
| 6 — Signing | `jarsigner -verify -verbose lumora-pos-backend.jar` | "jar verified" |

**Negative test — confirm this is not theatre:** allocate one engineer one day with no prior context to attempt to extract the variance-math algorithm from a release build. If they succeed in <2 hours, a layer is missing or misconfigured. If it takes >1 day with discoverable signs of tampering, the stack is doing its job.

---

## 7. Effort estimate

| Layer | Net engineering days | Skill required |
|---|---|---|
| 1 — Build hygiene | 0.5 | Junior |
| 2 — ProGuard backend | 1.5–2 | Mid (Spring familiarity) |
| 3 — Bytenode Electron | 1 | Mid (Node) |
| 4 — asar integrity | 0.25 | Junior |
| 5 — DPAPI secrets | 1 | Mid (Electron + Windows API understanding) |
| 6 — Code signing | 0.5 + cert procurement (weeks) | Mid + cert custodian |
| 7 — GraalVM native-image | 5–15 (v2) | Senior (Spring Native) |

**Recommended v1 cut-line: Layers 1 through 5.** Total: ~5 engineering days on top of the desktop build itself. Layer 6 ships as soon as the EV cert lands. Layer 7 is a deliberate v2 investment.

---

## 8. Out of scope

- **Postgres TDE** (transparent data encryption) — enterprise-only Postgres feature; not available in the binaries-only zip from EnterpriseDB. Use BitLocker at the OS level if customers require encrypted storage.
- **License key enforcement** — separate concern (commercial licensing, not anti-RE). Track in a different doc when needed.
- **Secure boot / TPM-bound keys** — overkill for a retail POS; revisit only if a regulated vertical demands it.
- **Macros for protecting the renderer JS bundle** — accepted as low-value (§ 2). The same code is publicly served by the Web SKU, so protecting it in the Desktop SKU is performative.
