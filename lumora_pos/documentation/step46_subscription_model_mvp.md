# Research: SaaS Subscription Base Model (MVP focus on Small Scale)

## 📌 Research Topic
Structuring a 3-tier SaaS subscription model (Small, Medium, Enterprise) for the Lumora POS System and identifying the absolute minimum technical requirements to implement the "Small Scale" tier immediately, leaving room for future scalability.

---

## 🏢 Industry Best Practices (Benchmarking Square & Lightspeed)
Leading POS platforms like Square, Lightspeed, and Toast utilize feature-gating and capacity limits to differentiate their tiers. 
For an entry-level / small business tier, standard limits generally include:
- **Location/Branch Limit:** Restricted to exactly 1 location.
- **Staff Limit:** Limited user accounts (typically 2 to 5 employees, excluding the owner).
- **Core Operations Only:** Full access to standard POS cart, inventory CRUD, and basic reporting.
- **Locked Modules:** Advanced operations like multi-location stock transfers, advanced accounting integrations (Xero/Quickbooks), open API access, and advanced CRM/Loyalty are strictly disabled or hidden behind paywalls.

---

## 🎯 Recommended Approach: The MVP SaaS Enforcer Structure
To launch the small business tier *right now* without over-engineering building complex billing pipelines, we need to introduce a **Tenant Plan & Quota Management** layer. 

We will define three future tiers (`SMALL`, `MEDIUM`, `ENTERPRISE`), but we will only actively build enforcement for the limitations associated with the `SMALL` tier for now.

### Priority Elements to Apply Now (The Essentials):

1. **Database Update: Tenant Plan Entity Expansion**
   - We must update the `Tenant` entity (or create a new `TenantSubscription` table) to track the active plan.
   - Fields needed: `planType` (Enum: SMALL, MEDIUM, ENTERPRISE), `status` (ACTIVE, TRIAL, SUSPENDED).
   - *For now, manually default all new registrations to the `SMALL` plan.*

2. **Backend: Quota & Feature Flag Interceptor**
   - Create a centralized method/service `SubscriptionValidationService` to check limits before executing service logic.
   - **Limit Checks Required Now:**
     - **Branch Creation:** Prevent creating a second branch if `planType == SMALL`.
     - **User Creation:** Prevent creating staff members beyond a set hard limit (e.g., max 5 users).
   - **Feature Locks Required Now:**
     - Restrict access to `StockTransferController` (multi-location logic).

3. **Frontend: Subscription Overview UI & UI Locks**
   - Add a "Subscription & Billing" tab in Settings.
   - Display their current plan ("Small Business Tier") and usage meters (e.g., "Locations: 1/1 used", "Staff: 3/5 used").
   - **UI Guards:** Hide or add padlock icons to the "Stock Transfers" or "Integrations" side-menu items. If clicked, show an "Upgrade your plan" modal.

4. **Future-Proof Payment Gateway Readiness**
   - Do *not* build manual credit card handling tables. In the next iterations, we will plug Stripe Billing directly into the `TenantSubscription` table, using Webhooks to push status updates (`TRIAL` -> `ACTIVE`).

---

## ⚙️ Technical Reasoning
Why this approach is optimal:
- **Fast Time-to-Market:** By hard-coding the `SMALL` tier constraints now and manually managing the payment/status off-platform (or using trials), you can launch immediately. 
- **Graceful Degradation:** By locking out `StockTransfer` features now, you prevent users from creating complex data states that would break if you downgrade them. 
- **Security:** Backend enforcement using Spring Interceptors or AOP (Aspect-Oriented Programming) guarantees that a savvy user cannot bypass UI locks by calling APIs directly.

---

## ⚠️ Risks & Limitations
- **Data Entanglement:** If a user on `ENTERPRISE` creates 5 branches, and downgrades to `SMALL`, we need a strategy to handle the other 4 branches (e.g., marking them inactive). *Mitigation: Prevent downgrades via API if usage exceeds the lower tier's limits.*
- **API Response Handling:** We must ensure the frontend gracefully handles a generic `403 Forbidden` or a custom `402 Payment Required` when hitting a locked endpoint, rather than crashing the UI.

---

## 📝 Immediate Implementation Guidelines (Next Steps)
1. **Define the Enums:** Create a `PlanType` enum in the backend (`SMALL`, `MEDIUM`, `ENTERPRISE`).
2. **Database Migration:** Add `plan_type` to your tenant-tracking logic/tables.
3. **Backend Service:** Build `SubscriptionGuard` class with methods like `.checkBranchLimit(tenantId)` and `.checkFeatureAccess(tenantId, Feature.STOCK_TRANSFERS)`.
4. **Apply to Controllers:** Inject `SubscriptionGuard` into the `BranchController` and `UserController` creation endpoints.
5. **Frontend Setup:** Build the basic usage-meter UI components to show the user their current standing.
