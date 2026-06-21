# Roadmap: Phase 3 & Phase 4 Transition

**Date:** 2026-03-06
**Context:** This document outlines the explicit next steps required to transition the Lumora POS from a functional enterprise system (Phase 1 & 2) into a fully-fledged, production-ready SaaS product with deep integrations and enterprise hardening.

---

## 🟢 Current Implementation Status

- **Core POS Engine:** ✅ Active. Cart, dynamic taxes, multi-tenancy, and receipt generation are complete.
- **Security & Auth:** ✅ Active. JWT, RBAC (Role-Based Access Control), and Pin Login are complete.
- **Supply Chain (Just Finished):** ✅ Active. Suppliers, Purchase Orders, and Stock Receiving are fully functional.
- **Financial Operations:** ✅ Active. Sales Reporting, Inventory Valuation, Complex Returns, and Paginated Analytics are complete.
- **UI/UX:** ✅ Active. The "Solid Blue" (#4590FF) enterprise transition is complete and deployed across all tables and modals.

---

## 🗺️ The Roadmap (Next Steps)

### Step 41: Multi-Branch Stock Transfers & Alerts (Finish Phase 2)

- **Objective:** Complete the logistics loop.
- **Backend:** Create a `StockTransfer` entity to track inventory moving from Warehouse A to Branch B. Implement automated flags when items drop below their defined `stockThreshold`.
- **Frontend:** Add a "Transfers" UI to the Inventory tab and a "Low Stock Alert" widget to the Dashboard.

### Step 42: Cashier Time-Clock & Shifts (Finish Phase 2)

- **Objective:** Enable HR functionality within the POS.
- **Backend:** Implement a `TimeRecord` entity to track clock-in/clock-out timestamps linked to the `Employee` entity.
- **Frontend:** Add a "Clock In" lock screen overlay on the POS Terminal that prompts for a PIN, and a Timesheet report page for Managers.

### Step 43: Tiered Loyalty & CRM System (Start Phase 3)

- **Objective:** Customer retention and marketing capability.
- **Backend:** Implement `LoyaltyAccount` logic where customers earn points based on their transaction subtotal (e.g., 1 point per $1 spent).
- **Frontend:** Update the POS Checkout Panel to display a customer's point balance and allow them to "Redeem Points" for discounts at the register.

### Step 44: Payment Gateway Integration (Stripe / Square)

- **Objective:** Real digital payment processing (PCI Compliant).
- **Backend:** Implement the `PaymentStrategy` pattern utilizing Stripe API or Square Web Payments SDK.
- **Frontend:** Integrate the hosted card element. When a cashier clicks "Card" at checkout, it securely prompts the physical card reader. Raw card data never touches our database.

### Step 45: Offline Mode & Data Sync (Start Phase 4)

- **Objective:** Ensure the system functions flawlessly during internet outages.
- **Frontend:** Implement a local-first **IndexedDB** browser database. If the internet fails, sales are saved locally.
- **Backend:** Create a conflict-resolution Sync Queue that processes bulk payloads of offline transactions the moment the frontend detects the internet is back online.

### Step 46: Accounting Ledger Sync (QuickBooks / Xero)

- **Objective:** Enterprise financial compliance.
- **Backend:** Build automated, overnight CRON jobs (`@Scheduled`) to execute bulk REST payload exports of Z-Reports, Sales, and Purchase Orders directly into third-party accounting ledgers.
