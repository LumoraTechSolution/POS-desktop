# Production deployment (Vercel + Render)

Frontend → **Vercel** (Next.js). Backend → **Render** (Spring Boot, Docker).
Database → **Render Postgres** (or Neon/Supabase). Each app is its own GitHub
repo, so they deploy independently.

> The three repos are independent (not real submodules). Point each platform at
> the matching repo: `pos-frontend` → Vercel, `pos-backend` → Render.

---

## Profiles & demo data

- `prod` — real production. Loads **only** `db/migration` (schema). The
  known-password demo accounts (demo tenant admin / cashier) live in
  `db/seed-dev` and are **never** loaded. Swagger off; actuator = `health,info`;
  `cookie-secure: true`.
- `demo` — adds the `db/seed-dev` seeds on top. Activate **alongside** another
  profile (`SPRING_PROFILES_ACTIVE=prod,demo`) when you want a ready demo login.
  The local Docker stack and the e2e suite use `prod,demo`.
- `dev` — local `./mvnw spring-boot:run`; includes the demo seeds.

**Real production must run `prod` ALONE** so the database starts clean. Start
from a **fresh** database.

---

## 1. Database

Create a Postgres instance and note host / db name / user / password. Render's
*Internal Database URL* is `postgresql://…` — the app needs the **JDBC** form
with credentials passed separately (see `DATABASE_URL` below).

## 2. Backend → Render (Web Service, Docker)

- New **Web Service** → connect `pos-backend` → Runtime **Docker** (uses the
  repo `Dockerfile`).
- **Health check path:** `/actuator/health`.
- Plan: **Starter or higher** — the free tier sleeps (≈50s cold start) and may
  OOM Spring Boot at 512 MB. Free Render Postgres also expires after 90 days.
- **Environment variables:**

  | Key | Value |
  |---|---|
  | `SPRING_PROFILES_ACTIVE` | `prod` |
  | `DATABASE_URL` | `jdbc:postgresql://<host>:5432/<db>` ← **note `jdbc:` prefix** |
  | `DB_USERNAME` | `<user>` |
  | `DB_PASSWORD` | `<password>` |
  | `JWT_SECRET` | 64+ random chars (`openssl rand -base64 48`) |
  | `ALLOWED_ORIGINS` | `https://<your-vercel-domain>` (comma-separate for several) |
  | `APP_BASE_URL` | `https://<your-render-backend>.onrender.com` |
  | `APP_UPLOAD_DIR` | `/var/lumora/uploads/logos` (see disk below) |
  | `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` |

### Persistent disk for tenant logos (required)

Logo uploads are written to the local filesystem (`LogoStorageService`). Render
containers have an **ephemeral** filesystem — without a disk, uploaded logos
vanish on every deploy/restart.

- Add a **Render Disk** to the backend service, mount path e.g. `/var/lumora`,
  size 1 GB.
- Set `APP_UPLOAD_DIR=/var/lumora/uploads/logos` (the app creates the dir).
- Note: a mounted disk pins the service to a **single instance** (no horizontal
  scale). To scale out later, migrate logo storage to object storage (S3 /
  Cloudflare R2) — a follow-up, not required for launch.

## 3. Frontend → Vercel

- Import `pos-frontend` (Vercel uses its own Next.js builder, not the Dockerfile).
- **Environment variable:** `NEXT_PUBLIC_API_URL = https://<your-render-backend>.onrender.com`
- Deploy, then put the Vercel URL into the backend's `ALLOWED_ORIGINS` and
  redeploy the backend.

## 4. Cross-site cookies (important)

`api.ts` sends `withCredentials: true`. With the app on `*.vercel.app` and the
API on `*.onrender.com` (different sites), browsers treat the refresh cookie as
third-party and may drop it. The access token is a `Bearer` header (works), but
refresh can fail.

**Fix:** serve both under one parent domain so cookies are same-site —
`app.yourdomain.com` (Vercel) + `api.yourdomain.com` (Render). Set
`ALLOWED_ORIGINS=https://app.yourdomain.com` and `NEXT_PUBLIC_API_URL=https://api.yourdomain.com`.

## 5. Post-deploy checklist

1. Log in at `/system-admin/login` as `superadmin@lumora.com` / `SuperAdmin@2024`
   → **change the password immediately** (it's a seeded default).
2. Confirm **no demo accounts** exist (running `prod` alone, they were never
   seeded). If you see `admin@demo.lumora.com`, the `demo` profile is active —
   remove it.
3. `GET /actuator/health` → `UP`; confirm `/swagger-ui.html` is **disabled**.
4. Provision a real tenant, log in, ring a sale, run a payment correction,
   upload a logo and confirm it survives a redeploy.

---

## Local stack (unchanged)

`docker compose up -d` in `lumora_pos/` runs `SPRING_PROFILES_ACTIVE=prod,demo`,
so the demo tenant admin/cashier exist and the e2e suite works as before.
