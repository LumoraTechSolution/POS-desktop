# Step 53: SaaS Feature Governance & Plan Quota Enforcement

## 📁 Objective
Hardening the SaaS governance layer by strictly enforcing feature-tier restrictions and individual plan resource quotas (Users, Locations, Products) in the Lumora POS UI.

## 🛠️ Implementation Details

### 1. Backend: Metadata Expansion
Updated the authentication layer to provide more than just roles. The `AuthResponse` now includes the tenant's current plan limits directly.
- **DTO Modification**: Added `maxLocations`, `maxUsers`, and `maxProducts` to the `UserDto` within `AuthResponse.java`.
- **Logic Refactoring**: Updated `AuthService.mapToDto` to fetch these values from the `TenantConfigurationEntity` during login/refresh.
- **Governance Check**: Added a critical validation in `AuthService.login` and `AuthService.refreshToken` to verify that the tenant's account is `active` and their subscription hasn't `expired`.

### 2. Frontend: State Hydration
Updated `authStore.ts` to persist the new metadata, allowing the frontend to reactively enforce limits without extra API calls.
- **User Interface**: Updated the `User` interface to include the `max*` limit properties.
- **Feature Flagging**: Ensured that the list of `featuresEnabled` is consumed by the `FeatureGuard` component to gate specific UI blocks.

### 3. Module Hardening (UI Leakage)
Secured the following modules which were previously accessible or partially "leaking" to lower-tier tenants:

#### **A. Time Clock & Staff Management**
- Gated the "Clock In/Out" widget in `POSHeader.tsx` using `FeatureGuard`.
- Gated the "View Timesheets" button in `EmployeesPage`.
- Applied a professional "Feature Not Available" fallback to the `TimesheetsPage` itself as defense-in-depth against direct URL access.

#### **B. Tax Configuration**
- Wrapped the entire "Tax Configuration" block in `SettingsPage.tsx` with `FeatureGuard`.
- Added a custom premium fallback UI (locked icon + upgrade CTA) to entice users to upgrade if they try to access advanced tax rules.

#### **C. Resource Quota Enforcement**
Implemented visual feedback and button disabling to prevent tenants from exceeding their plan's hard-coded limits:
- **Employee Management**: Disables the "Add Employee" button and shows a "Limit reached" status when the `maxUsers` cap is hit.
- **Branch Management**: Disables the "Add Branch" button if the `maxLocations` limit is reached (e.g., 1 for Small Business).
- **Product Inventory**: Added a product count tracker in the header and disabled the "Add Product" button if the `maxProducts` limit is hit.

## 🛡️ Security & Performance
- **Fail-Safe**: If a tenant's configuration is missing, the system defaults to the most restrictive plan (Small Business) to prevent accidental access to premium features.
- **Zustand Persistence**: Plan limits are persisted in local storage, ensuring that the guardrails remain active even after a page refresh.

## 📊 Feature Tier Mapping (Reference)
| Plan | Features Allowed | Quotas |
| :--- | :--- | :--- |
| **Small** | Sales, Inventory, Reports, Customers, Employees | 1 Branch, 5 Users, 500 Products |
| **Medium** | All above + Purchase Orders, Returns, Tax Config | 3 Branches, 15 Users, 5000 Products |
| **Enterprise** | All above + Stock Transfers, Time Clock, Advanced Analytics, API Access | Unlimited |

---
**Next Step**: Perform a final regression test using dummy accounts from different tiers to verify that the UI renders correctly for each plan.
