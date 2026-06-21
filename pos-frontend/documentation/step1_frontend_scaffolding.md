# Frontend Architecture — Step 1: Scaffolding

**Date**: February 17, 2026  
**Status**: ✅ Completed  
**Stack**: Next.js 14.2.35 | TypeScript | Tailwind CSS | Zustand | React Query

---

## Files Created

### Root Config

| File                 | Path        | Purpose                                                                                                                                             |
| -------------------- | ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `package.json`       | `frontend/` | Project manifest. Dependencies: `next`, `react`, `react-dom`, `zustand`, `@tanstack/react-query`, `axios`, `lucide-react`, `clsx`, `tailwind-merge` |
| `next.config.mjs`    | `frontend/` | Next.js configuration (default)                                                                                                                     |
| `tailwind.config.ts` | `frontend/` | Tailwind CSS configuration                                                                                                                          |
| `tsconfig.json`      | `frontend/` | TypeScript config with `@/` path alias for `src/`                                                                                                   |
| `postcss.config.mjs` | `frontend/` | PostCSS with Tailwind plugin                                                                                                                        |
| `.eslintrc.json`     | `frontend/` | ESLint with `next/core-web-vitals` preset                                                                                                           |

---

### App Routes (`src/app/`)

| File          | Path       | Purpose                                                                                                                     |
| ------------- | ---------- | --------------------------------------------------------------------------------------------------------------------------- |
| `layout.tsx`  | `src/app/` | **Root layout** — Inter font (Google Fonts), `<QueryProvider>` wrapper, PWA manifest link, global CSS import                |
| `page.tsx`    | `src/app/` | **Root page** — Auth-based redirect: authenticated → `/terminal`, unauthenticated → `/login`. Shows spinner during redirect |
| `globals.css` | `src/app/` | Global Tailwind CSS imports (`@tailwind base/components/utilities`)                                                         |

---

### Auth Route Group (`src/app/(auth)/`)

| File         | Path                    | Purpose                                                                                                                                                                                                   |
| ------------ | ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `layout.tsx` | `src/app/(auth)/`       | **Auth layout** — Full-screen centered with dark gradient background (`gray-950 → indigo-950`)                                                                                                            |
| `page.tsx`   | `src/app/(auth)/login/` | **Login page** — Two modes: (1) Email/password form, (2) PIN pad with 4-digit input. Toggle between modes via segmented control. Premium glassmorphism design with dark theme. Touch-friendly PIN buttons |

---

### POS Route Group (`src/app/(pos)/`)

| File         | Path                      | Purpose                                                                                                                                                                                                                     |
| ------------ | ------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `layout.tsx` | `src/app/(pos)/`          | **POS layout** — Full-screen dark background (`bg-gray-950`)                                                                                                                                                                |
| `page.tsx`   | `src/app/(pos)/terminal/` | **POS Terminal** — Split layout: Left = header bar + search bar + product grid (4-col skeleton), Right = cart panel (items list + subtotal/tax/total + "Pay Now" button). Shows terminal number and online status indicator |

---

### Dashboard Route Group (`src/app/(dashboard)/`)

| File         | Path                            | Purpose                                                                                                                                                                                            |
| ------------ | ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `layout.tsx` | `src/app/(dashboard)/`          | **Dashboard layout** — Sidebar (264px) with navigation links (Overview, Products, Inventory, Customers, Employees, Reports, Settings) + "Open POS Terminal" button. Main content area on the right |
| `page.tsx`   | `src/app/(dashboard)/overview/` | **Dashboard overview** — 4 KPI metric cards (Today's Sales, Transactions, Avg Order Value, Active Products) + 2 chart placeholders (Sales Trend, Top Products)                                     |

---

### Stores (`src/stores/`)

| File           | Purpose                                                                                                                                                                                                                                                                                 |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `authStore.ts` | **Zustand auth store** with `persist` middleware (localStorage key: `lumora-pos-auth`). State: `user`, `token`, `isAuthenticated`. Actions: `setAuth()`, `logout()`, `hasPermission()`, `hasRole()`. User shape: `{ id, tenantId, email, firstName, lastName, roles[], permissions[] }` |

---

### Services (`src/services/`)

| File     | Purpose                                                                                                                                                                                                                                                                    |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `api.ts` | **Axios instance** configured with base URL from `NEXT_PUBLIC_API_URL` env var (default `http://localhost:8080`). Request interceptor: attaches `Authorization: Bearer <token>` and `X-Tenant-ID` headers. Response interceptor: auto-logout + redirect to `/login` on 401 |

---

### Components (`src/components/`)

| File                | Path                    | Purpose                                                                                                                    |
| ------------------- | ----------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `QueryProvider.tsx` | `components/providers/` | **React Query provider** — Creates `QueryClient` with defaults: 30s stale time, 5min GC time, 1 retry, no refetch-on-focus |

---

### Lib (`src/lib/`)

| File       | Purpose                                                                                                                                                      |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `utils.ts` | Utility functions: `cn()` (Tailwind class merge via `clsx` + `tailwind-merge`), `formatCurrency()` (Intl.NumberFormat), `formatDate()` (Intl.DateTimeFormat) |

---

### Public Assets (`public/`)

| File            | Purpose                                                                                                                                |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `manifest.json` | PWA manifest: name "Lumora POS", standalone display, dark theme (#030712 bg, #6366f1 theme), icon placeholders for 192x192 and 512x512 |

---

## Route Structure Summary

```
/                       → Redirect (→ /terminal or → /login)
/login                  → Auth: Email/Password + PIN pad
/terminal               → POS: Product grid + Cart + Payment
/overview               → Dashboard: KPI cards + Charts
/products               → (Planned Step 3)
/inventory              → (Planned Step 3)
/customers              → (Planned Phase 2)
/employees              → (Planned Phase 2)
/reports                → (Planned Step 6)
/settings               → (Planned Phase 2)
```

---

## Build Verification

```
✅ npm run build
  ✓ Linting and checking validity of types
  ✓ Collecting page data
  ✓ Generating static pages (8/8)
  ✓ Collecting build traces
  ✓ Finalizing page optimization
```

---

## Key Design Decisions

1. **Route Groups**: `(auth)`, `(pos)`, `(dashboard)` — separate layouts without affecting URL paths
2. **Zustand + persist**: Auth state survives page refresh via localStorage
3. **React Query**: Server-state (products, customers, etc.) managed separately from UI state
4. **Axios interceptors**: JWT and tenant ID injected automatically on every request
5. **PIN pad login**: Touch-friendly numeric keypad for cashier quick-access (no keyboard needed)
6. **Dark theme throughout**: `gray-950` base with `indigo` accent for premium POS appearance
