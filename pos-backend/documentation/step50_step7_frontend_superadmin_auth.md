# Step 50 — Step 7: Frontend Super Admin Auth & Layout Routing

## Summary
The Super Admin dashboard requires a highly secure, completely isolated frontend pathway to prevent cross-contamination with the regular tenant application state.

### 1. Isolated State Management (`superAdminStore`)
Created a dedicated Zustand store `useSuperAdminStore` for the Super Admin session. This prevents the primary `authStore` (which stores the tenant-scoped `X-Tenant-ID`) from holding platform-level credentials, eliminating the risk of a super admin token being accidentally sent to a standard endpoint.

### 2. Isolated API Client (`superAdminApi`)
Instead of using the standard `api.ts`, which auto-injects `X-Tenant-ID`, we created `superAdminApi.ts`. This Axios instance:
- Bases all calls on `/api/v1/super-admin`
- Only injects the super admin JWT.
- Automatically handles 401s by clearing `superAdminStore` and redirecting strictly to `/super-admin/login`.

### 3. Isolated Layout Structure (`app/(super-admin)`)
Utilized Next.js 14 Route Groups to wrap all Super Admin pages in an exclusive layout:
- **`layout.tsx`**: Provides the dark/light premium admin sidebar and ensures that if `!isAuthenticated`, the user is hard-redirected to the login page (acting as a strict client-side guard).
- **`login/page.tsx`**: A highly premium, dark-mode focused secure login gateway restricted for the `adminEmail`.
- **`super-admin/page.tsx`**: The main dashboard landing page fetching the platform KPIs from `GET /stats`.

## Next Steps
This framework sets up the foundation. The next phase will build the interactive tables to list tenants, view their details, and spawn new businesses entirely from the UI.
