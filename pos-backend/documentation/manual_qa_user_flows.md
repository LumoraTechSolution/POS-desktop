# 🧪 Lumora POS - Manual QA User Flow Guide

This document provides a structured, step-by-step checklist to manually verify all core features of the Lumora POS SaaS Platform. It is designed to be executed sequentially to ensure realistic data flow (e.g., creating products before attempting to sell them).

---

## 1. Security & Authentication Flow
**Objective:** Verify rate-limiting, JWT scoping, and route protection.

- [ ] **Test Rate Limiting:** Attempt to log into `http://localhost:3000/login` with the wrong password 11 times. The 11th attempt should yield a "Too Many Requests" warning message.
- [ ] **Test Success Login:** Log in with correct tenant credentials (e.g., `admin@lumora.com` / `password`).
- [ ] **Test Middleware Protection:** Copy your current URL, open an **Incognito Window**, and paste the URL. You should be instantly redirected back to the `/login` screen.
- [ ] **Test Token Expiry/Refresh (Optional):** Wait (or simulate) token expiration time and observe if the frontend correctly requests a fresh token without logging you out.

---

## 2. Super Admin Governance Flow (SaaS Layer)
**Objective:** Verify the platform operator can manage tenants.

- [ ] **Access Panel:** Navigate to `http://localhost:3000/system-admin/login` and authenticate as `superadmin@lumora.com` / `SuperAdmin@2024`.
- [ ] **View Tenants:** Ensure the "Demo Business" tenant appears in the tenants list.
- [ ] **Audit Logs:** Navigate to the Super Admin audit tab. Filter the logs by "Today". Verify that the logs accurately reflect your recent login attempts (including the failed rate-limit attempts).
- [ ] **Feature Guards:** Modify a tenant's configuration to toggle off the "REPORTS" feature. 
- [ ] **Verify Guard:** Log into the POS as that tenant and confirm the "Reports" routing link is hidden or blocked. (Remember to turn it back on afterwards).

---

## 3. Inventory Management Flow
**Objective:** Verify N+1 fixes, derived stock formulas, and bulk capability.

- [ ] **Access Inventory:** Log into the tenant dashboard and navigate to the Inventory page.
- [ ] **Create Category & Brand:** Create a new category ("Beverages") and a brand ("Lumora Cola").
- [ ] **Add Single Product:** Add a new product (e.g., "Cola 500ml"). Set Base Price: $2.50, Cost Price: $1.00, Threshold: 10. **Do not add stock yet.**
- [ ] **Add Stock Retrieval:** Execute a Stock Transfer/Addition to add 50 units of Cola to the Default Branch.
- [ ] **Verify Formula Match:** Ensure the main inventory table accurately reflects `50` units without requiring manual double-entry.
- [ ] *(Optional)* **Bulk Export/Import:** Export the inventory to CSV. Add a new row in Excel. Import the CSV back. Ensure the new product appears and no database constraint errors trigger.

---

## 4. POS Terminal & Checkout Flow
**Objective:** Verify the critical financial path, pessimistic locking, and taxation.

- [ ] **Open POS:** Navigate to the Terminal interface.
- [ ] **Add Items:** Scan or search for "Cola 500ml". Add 3 units to the cart.
- [ ] **Apply Discount:** Apply a 10% discount to the cart.
- [ ] **Review Totals:** Verify the Math:
  - Subtotal equals $7.50.
  - Discount is -$0.75.
  - Tax computation (based on the tenant's base configuration) is applied to the net value ($6.75).
- [ ] **Complete Sale:** Process the payment via CASH.
- [ ] **Invoice Verification:** Check the resulting receipt for the `INV-XXXXXXXX` identifier and assert it is unique.
- [ ] **Verify Stock Deduction:** Immediately return to the Inventory page. The stock for Cola 500ml must now be `47` (50 - 3). It must deduct exactly.
- [ ] **Insufficient Stock Test:** Return to POS. Attempt to ring up 100 Colas. The system **must block the sale** with an "Insufficient Stock" exception rather than dipping into negative stock values.

---

## 5. Dashboard Analytics & Reporting Flow
**Objective:** Verify data aggregation and JSON logging.

- [ ] **View Dashboard KPIs:** Navigate to the main Dashboard overview. 
- [ ] **Check Real-Time Updates:** The "Today's Sales" KPI, "Transactions", and "Net Sales" figures should reflect the $6.75+Tax Cola transaction you just processed.
- [ ] **Payment Breakdown:** The pie chart / breakdown should show 100% of today's revenue in the "CASH" category.
- [ ] **Generate Report:** Navigate to the Reports tab. Generate a detailed "Sales Summary" for the current month. Ensure the PDF/CSV generation completes flawlessly.

---

## 6. Resilience & Dev-Ops Check
**Objective:** Verify the environment is bulletproof.

- [ ] **React Error Boundary:** Manually induce a frontend error (e.g., temporarily modifying your frontend code to throw a `new Error()` inside a dashboard widget). The screen should NOT go blank white. The new "Something went wrong" UI should appear with a "Try again" button.
- [ ] **Check JSON Logs:** Open your backend terminal. Verify that the server logs are outputting structured JSON strings rather than plain text, signifying readiness for ELK stack ingestion.
- [ ] **Inspect API Specs:** Open `http://localhost:8081/swagger-ui.html`. Make sure no internal routes (like `/actuator/env`) are visible or accessible without JWT authorization.

---
**Status:** Ready to execute! 🚀
