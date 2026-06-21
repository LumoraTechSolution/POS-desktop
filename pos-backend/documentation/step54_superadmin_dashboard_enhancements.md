# Super Admin Dashboard & MRR Enhancements

## Overview
This step focuses on enhancing the Super Admin Dashboard by adding real-time financial tracking and visual tier distribution, transforming it into a functional platform command center.

## Key Changes
1. **MRR (Monthly Recurring Revenue) Calculation:**
   - Modified `SuperAdminTenantService` to calculate MRR dynamically based on the platform's active tenant tier counts.
   - Assigned estimated values: Small Business = $50, Medium = $150, Enterprise = $500. *(Represented natively in LKR for local localization)*.
   - Updated `PlatformStatsResponse.java` and `superAdmin.ts` to include `projectedMrr`.

2. **Dashboard Visualizations:**
   - Replaced the placeholder chart with a `recharts` PieChart for "Tier Distribution".
   - Replaced dummy layout grids with modern responsive data blocks (grid layouts modified to span up to 5 stat cards).
   - Added a dedicated "Projected MRR" stat card on the main dashboard (`app/(super-admin)/super-admin/page.tsx`).
   - Added "Revenue Opportunities" widget to flag pending expirations and upgrade potentials.

3. **Sidebar Navigation Improvements:**
   - Updated `(super-admin)/layout.tsx` to include pathname tracking (`usePathname`) for dynamic active state highlights across "Dashboard" vs "Tenants".

## Impact
- **Visibility**: Super Admins now have immediate, concrete financial visibility.
- **Actionability**: Easily track platform growth opportunities based on raw tier counts and expirations.
- **UX**: Sidebar prevents confusion by correctly showing exactly where the admin is within the Super Admin portal.
