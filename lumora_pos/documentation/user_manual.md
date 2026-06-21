# Lumora POS System — User Manual

**Version:** 1.0 (Phase 1 + 2 release)
**Audience:** Cashiers, Managers, and Administrators of Lumora-powered businesses
**Last Updated:** 2026-04-30

---

## Table of Contents

1. [Getting Started](#1-getting-started)
2. [Roles & Permissions](#2-roles--permissions)
3. [The POS Terminal (Cashier Workflow)](#3-the-pos-terminal-cashier-workflow)
4. [Returns, Refunds & Exchanges](#4-returns-refunds--exchanges)
5. [Time Clock (Shifts)](#5-time-clock-shifts)
6. [Inventory Management](#6-inventory-management)
7. [Suppliers & Purchase Orders](#7-suppliers--purchase-orders)
8. [Stock Transfers Between Branches](#8-stock-transfers-between-branches)
9. [Customers & Loyalty](#9-customers--loyalty)
10. [Employees & User Management](#10-employees--user-management)
11. [Branches](#11-branches)
12. [Tax Configuration](#12-tax-configuration)
13. [Reports & Analytics](#13-reports--analytics)
14. [Settings](#14-settings)
15. [Hardware Setup](#15-hardware-setup)
16. [Troubleshooting & FAQ](#16-troubleshooting--faq)
17. [Glossary](#17-glossary)

---

## 1. Getting Started

### 1.1 What you need
- A modern web browser (Chrome, Edge, or Firefox — latest two versions).
- A working internet connection.
- Your tenant URL (e.g. `https://yourcompany.lumora.app`).
- Login credentials issued by your administrator.

### 1.2 Logging in
1. Open your tenant URL in the browser.
2. On the **Login** page, enter your email and password.
3. Click **Sign In**.

You will land on the **Overview Dashboard** if you are an Admin or Manager, or directly on the **POS Terminal** if you are a Cashier.

### 1.3 Logging in with a 4-digit PIN (Cashiers)
For shift handovers and quick mid-day logins, cashiers can use a numeric PIN instead of typing their full password.

1. From the login screen, choose **PIN Login**.
2. Enter your 4-digit PIN.
3. Press **Enter**.

If your PIN has not been set yet, ask your manager to assign one from **Employees → [your profile] → Set PIN**.

### 1.4 Logging out
Click your name in the top-right corner of any page and choose **Sign Out**. Always sign out at the end of your shift, especially on shared terminals.

---

## 2. Roles & Permissions

The system has three primary roles. The menu items and buttons you see depend on the role you were assigned.

| Role | Can do |
|---|---|
| **Cashier** | Use the POS Terminal, look up products, take payments, clock in/out, view customers (limited). |
| **Manager** | Everything a cashier can do, plus inventory updates, returns approval, viewing all reports, managing customers, supervising shifts. |
| **Admin** | Everything a manager can do, plus creating/disabling users, managing branches, tax configuration, supplier and purchase order management, and system settings. |

If you cannot see a menu item described in this manual, your role does not have access. Ask your administrator.

---

## 3. The POS Terminal (Cashier Workflow)

The POS Terminal is the full-screen checkout interface used to ring up sales. Open it from the sidebar (**POS Terminal**) or by navigating to `/terminal`.

### 3.1 Anatomy of the screen
- **Search bar (top):** Type a product name, SKU, or scan a barcode here.
- **Product grid (center):** Tap a product tile to add it to the cart.
- **Cart panel (right):** Live list of items, quantities, line totals, subtotal, tax, and grand total.
- **Customer field (right top):** Optional — link the sale to a customer.
- **Checkout button (right bottom):** Opens the payment dialog.

### 3.2 Adding items to the cart
There are three ways to add an item:
1. **Scan the barcode** — focus the search bar (it is focused by default), then scan. The item is added with quantity 1.
2. **Type a SKU or name** — the dropdown shows matches; press **Enter** or click the result.
3. **Tap a product tile** in the grid.

### 3.3 Adjusting the cart
- **Change quantity:** Use the `+` / `−` buttons next to the line, or click the quantity number to type it.
- **Remove an item:** Click the trash icon on the line.
- **Apply a line discount:** Click the discount icon on the line, enter an amount or percentage, confirm.
- **Clear the cart:** Click **Clear Cart** at the top of the cart panel (will ask to confirm).

### 3.4 Linking a customer (optional)
1. Click **Add Customer** in the cart panel.
2. Search by name, phone, or email.
3. Select the customer. The sale will count toward their purchase history and loyalty points.
4. To create a new customer on the fly, click **+ New Customer** in the search dropdown.

### 3.5 Taking payment
1. Click **Checkout**.
2. Choose a payment method:
   - **Cash** — Enter the amount tendered. Change due is calculated automatically.
   - **Card** — Charge the card on your separate terminal, then click **Confirm Card Payment** to record the transaction.
   - **Online** — For digital/QR payments. Confirm after receiving the notification on your provider's app.
   - **Split** — Divide the bill across two or more methods. Add each portion until the remaining balance is zero.
   - **Credit** — Place the sale on the customer's account (requires linked customer with credit privileges).
3. Click **Complete Sale**. The receipt opens in a print preview.

### 3.6 Printing the receipt
- Press **Print** in the preview to send to your default printer (typically a thermal receipt printer).
- Press **Email Receipt** to send to the linked customer's email (if enabled in settings).
- Press **Skip** to close without printing.

### 3.7 Voiding a sale
A sale can only be voided **before** payment is recorded — once printed, you must process a Return instead.
- To void the in-progress cart, click **Clear Cart** and confirm.

---

## 4. Returns, Refunds & Exchanges

Open **Sales History** (in the sidebar or from the dashboard), find the original transaction, and click **Process Return**.

### 4.1 Three return modes
| Mode | What happens |
|---|---|
| **Refund** | Money is refunded; stock is added back to inventory. |
| **Exchange** | Returned stock is added back; replacement items are deducted; a new linked sale is created. |
| **Damaged / Write-off** | Money is refunded but stock is **not** added back. |

### 4.2 Step-by-step
1. Open the original sale and click **Process Return**.
2. Select which **line items** to return and the quantity for each (partial returns are supported).
3. Select the return mode (Refund, Exchange, or Damaged Write-off).
4. Enter a **reason** — this field is required for compliance.
5. For **Exchange**, add the replacement product(s) using the search box.
6. Choose the **refund method**: Original payment, Cash, or Store Credit.
7. Click **Submit**.

### 4.3 Manager approval
Returns above your role's auto-approval threshold enter the **PENDING** state. A Manager or Admin must open the return and click **Approve** (or **Reject** with a note) before stock and money movements complete.

Lifecycle: `PENDING → APPROVED → COMPLETED` (or `REJECTED`).

---

## 5. Time Clock (Shifts)

### 5.1 Clocking in
1. After login, click **Clock In** in the top bar (or visit **Employees → My Timesheet**).
2. The system records the start time. You cannot clock in twice — you must clock out first.

### 5.2 Clocking out
1. Click **Clock Out** in the top bar.
2. Optionally add a **note** (e.g. "Closed register, restocked shelves").
3. Confirm.

### 5.3 Viewing your timesheet
Go to **Employees → Timesheets** to see your shift history. Managers and Admins see timesheets for all users on the same page, filterable by employee and date range.

---

## 6. Inventory Management

All inventory features live under the **Inventory** section in the sidebar.

### 6.1 Products (`Inventory → Products`)
- **Search & filter** by name, SKU, category, brand, or active status.
- **Add a product:** Click **+ New Product**. Required fields: name, SKU, base price, cost price, starting stock. Optional: barcode, category, brand, description, image URL, low-stock threshold.
- **Edit a product:** Click the product row to open the edit form.
- **Activate / deactivate quickly:** Use the **status toggle switch** on each product row — green = active (sellable), gray = inactive (hidden from POS). Every toggle is logged in the audit trail.

### 6.2 Scan-to-Add / Scan-to-Edit
On the Products page, simply scan a physical barcode:
- If the product **exists**, you jump to its Edit page (great for stock or price updates).
- If the product is **new**, you jump to the New Product form with the barcode already filled in.

This makes "walking inventory" updates fast — no typing required.

### 6.3 Categories & Brands
- **Inventory → Categories** and **Inventory → Brands** offer simple CRUD lists.
- Categories and brands are reusable across products; deactivating one hides associated products from filters but does not delete them.

### 6.4 Bulk import / export
Visit **Inventory → Products → Import / Export**:
- **Import:** Upload a CSV or Excel file. The system validates each row and reports any errors before committing.
- **Export:** Download the current catalog as CSV or Excel for editing offline or migrating data.

Tip: Always **Export first** to get a template that matches the expected column format.

### 6.5 Low stock alerts
Products whose stock has dropped below their **Low Stock Threshold** appear in:
- The **Low Stock** widget on the Overview Dashboard.
- A red badge on the product row in the Products list.

Adjust thresholds individually per product when editing.

### 6.6 Multi-location stock
If your tenant has more than one branch, each product has a **stock level per branch**. View a product's per-branch breakdown in its Edit page under **Stock Levels**. Inventory adjustments and sales always affect the branch you are logged into.

### 6.7 Manual stock adjustments
From a product's Edit page, click **Adjust Stock**:
1. Choose an adjustment type: `STOCK_IN`, `STOCK_OUT`, `RECONCILIATION`, `DAMAGE`.
2. Enter the new quantity (or delta).
3. Enter a reason.
4. Submit. The change is logged with previous and new quantities.

---

## 7. Suppliers & Purchase Orders

### 7.1 Suppliers (`Inventory → Suppliers`)
Maintain your vendor directory. Add a supplier with name, contact person, phone, email, and address. Suppliers must exist before you can raise purchase orders against them.

### 7.2 Purchase Orders (`Inventory → Purchase Orders`)
1. Click **+ New PO**.
2. Select a supplier and the receiving branch.
3. Add line items: product, quantity ordered, unit cost.
4. Save as **DRAFT** to keep editing, or **Submit** to send to the supplier (status becomes `SUBMITTED`).

### 7.3 Receiving stock
When goods arrive:
1. Open the PO and click **Receive Stock**.
2. For each line, enter the **quantity actually received** (may differ from ordered).
3. Click **Confirm Receipt**. Stock is added to the receiving branch atomically and the PO moves to `RECEIVED`.

Partial deliveries are supported — receive what arrived now and process the rest later.

---

## 8. Stock Transfers Between Branches

For tenants with multiple locations.

### 8.1 Creating a transfer
1. Go to **Inventory → Stock Transfers → + New Transfer**.
2. Choose **From Branch** and **To Branch**.
3. Add the products and quantities to transfer.
4. Save. Status: `PENDING`.

### 8.2 Lifecycle
- **PENDING** — Transfer created but not yet shipped.
- **IN_TRANSIT** — Mark as such when goods leave the source branch (stock is reserved).
- **COMPLETED** — Mark on arrival; the system atomically deducts from source and adds to destination.
- **CANCELLED** — Available before completion.

### 8.3 Viewing transfers
Filter by status, source, destination, or date. Click any transfer to see its full item list.

---

## 9. Customers & Loyalty

### 9.1 Customer list (`Customers`)
Search, filter, and add customers. Required: first name. Optional but recommended: phone and email for receipts and loyalty.

### 9.2 Customer profile
Open any customer to see:
- Contact details (editable).
- **Purchase history** — all linked sales with totals.
- **Loyalty points** — current balance and adjustment history.

### 9.3 Awarding loyalty points
Loyalty points accrue automatically based on linked sales. Manual adjustments can be made by a Manager from the customer profile (**Adjust Points** button) — every adjustment is logged.

---

## 10. Employees & User Management

**Admin only.** Open from **Employees** in the sidebar.

### 10.1 Creating a user
1. Click **+ New User**.
2. Enter name, email, role (Cashier / Manager / Admin), and an initial password.
3. Optionally assign a default branch and a 4-digit PIN.
4. Save.

### 10.2 Disabling a user
Use the **Active** toggle on the user row. Disabled users can no longer log in but their historical records (sales, time records, audit entries) remain intact. **Do not** delete users — disable them instead.

### 10.3 Resetting a password or PIN
On the user's edit page, click **Reset Password** or **Reset PIN**. Communicate the new credentials to the user via a secure channel.

---

## 11. Branches

**Admin only.** Open from **Branches** in the sidebar.

- **+ New Branch** — Add a name, address, phone, and (optionally) mark it as the default branch.
- **Default branch** — Used as the initial selection when an Admin/Manager logs in. Only one branch can be the default at a time.
- **Active toggle** — Inactive branches do not appear in branch selectors but their data is preserved.

---

## 12. Tax Configuration

**Admin only.** Open from **Settings → Tax Rates**.

- **+ New Tax Rate** — Enter a name (e.g. "VAT 8%"), the rate as a decimal (`0.0800` = 8%), an optional description, and whether it is the default.
- **Default tax** — Applied automatically to all new sales. You can have multiple tax rates active and switch between them per sale, but only one is the default.
- **Active toggle** — Deactivated rates are hidden from new sales but remain visible on historical transactions.

---

## 13. Reports & Analytics

All reports require Manager or Admin role. Open **Reports** in the sidebar.

### 13.1 Overview Dashboard (`Overview`)
At-a-glance KPIs on the home page:
- Today's sales vs yesterday's (with % change).
- Today's transactions and average order value.
- Active and total products.
- 7-day sales trend chart.
- Top 5 selling products.
- Payment method breakdown.
- Low stock alerts.
- Last 10 transactions feed.

### 13.2 Available reports
| Report | What it shows |
|---|---|
| **Sales Report** | Paginated, date-filtered sales with line-item drill-down. |
| **Inventory Valuation** | Total products, stock units, cost value, retail value, potential profit, plus category breakdown. |
| **Employee Performance** | Per-cashier transaction count, revenue, average sale, discounts given. |
| **Top Customers** | Customers ranked by transactions, total spent, loyalty points. |
| **Tax Summary** | Tax collected by payment method and overall. |
| **Profitability** | Per-product units sold, revenue, COGS, gross profit, margin %. |

### 13.3 Filtering and exporting
Most reports support a date-range picker, branch filter, and CSV/PDF export via the buttons above the table.

---

## 14. Settings

The Settings page (Admin only) contains:
- **Tax Rates** (see Section 12).
- **Business Profile** — Company name, address, logo (appears on receipts).
- **Receipt Footer** — Text printed at the bottom of every receipt (e.g. return policy).
- **System Health** — On-demand inventory health check that cross-references global stock counts against per-branch stock to detect discrepancies.

---

## 15. Hardware Setup

Lumora is designed to work with off-the-shelf retail hardware. No custom drivers required.

### 15.1 USB barcode scanner
Plug it into any USB port on the till computer. Most consumer scanners ship in **HID / Keyboard Wedge** mode by default — they "type" the barcode followed by Enter into the focused field. No software install needed. Test by opening a text editor and scanning a label.

### 15.2 Thermal receipt printer
1. Install the printer driver on the till computer (vendor-supplied).
2. In your operating system's printer settings, mark the thermal printer as the **default printer**.
3. Print a test page from the OS.
4. In Lumora, complete a test sale and verify the receipt prints automatically.

### 15.3 Cash drawer
Most cash drawers are **printer-driven** via an RJ11/RJ12 cable. Plug the drawer cable into the matching port on the back of the receipt printer. The drawer pops automatically when the printer cuts a receipt — no extra configuration in Lumora.

### 15.4 Card reader (standalone)
Run your bank-issued card terminal as a separate device. Type the sale total into the bank terminal, take payment there, then in Lumora select **Card** and click **Confirm Card Payment** to record the sale.

---

## 16. Troubleshooting & FAQ

**Q: I can't log in — "Invalid credentials".**
Double-check the email and that Caps Lock is off. If still failing, ask an Admin to reset your password. After 5 failed attempts, the account locks for 15 minutes.

**Q: A scanned barcode doesn't add the product.**
Make sure the search bar in the POS terminal is **focused** (cursor blinking inside it) before scanning. Click into the bar and try again. If the product is unknown, the system will show "No match" — add the product first via Inventory → Products.

**Q: A sale finished but no receipt printed.**
Check that your default printer is set correctly in the OS, that it's powered on with paper loaded, and that the print preview wasn't dismissed. You can re-print any receipt from **Sales History → [sale] → Reprint**.

**Q: Stock count looks wrong after a return.**
Confirm the return mode used. **Damaged Write-off** does **not** add stock back (by design). Use **Refund** to restore stock.

**Q: I can't see a menu item that this manual describes.**
Your user role does not have access. Ask an Admin to grant the appropriate role.

**Q: Two cashiers tried to sell the last unit at the same time — what happens?**
The system uses optimistic locking. The first sale to commit succeeds; the second cashier sees a "Stock changed, please retry" message and the cart re-validates against current stock.

**Q: How do I close out the day?**
1. Process any pending returns or voids.
2. Run **Reports → Sales Report** filtered to today and verify totals.
3. Reconcile the cash drawer against the cash row in the **Tax Summary / Payment Method Breakdown**.
4. Have all cashiers **Clock Out**.
5. Lock the till.

**Q: My internet went down — can I keep selling?**
Not yet. Offline mode is on the Phase 4 roadmap. Until then, sales require an internet connection to the Lumora backend.

---

## 17. Glossary

- **SKU** — Stock Keeping Unit. The unique internal code for a product.
- **Barcode** — The scannable code printed on a product (typically EAN-13 or UPC-A).
- **PO** — Purchase Order. A document sent to a supplier requesting goods.
- **COGS** — Cost of Goods Sold. The cost-side total used to compute gross profit.
- **Tenant** — Your business's isolated workspace inside Lumora's multi-tenant platform.
- **PIN** — A 4-digit numeric code used by cashiers for fast login.
- **Branch** — A physical store location. Stock is tracked per branch.
- **Audit Trail** — The immutable log of every important action (logins, sales, refunds, stock changes) for compliance.
- **Optimistic Locking** — A database technique that prevents two users from silently overwriting each other's changes.

---

## Need help?

Contact your administrator first. For platform-level support, open a ticket through the **Help** link in the sidebar (if your tier includes support) or email your Lumora account manager.

*This manual covers Phase 1 and Phase 2 features. New features (offline mode, integrated card readers, e-commerce sync, etc.) will be added as they are released — see the roadmap in the project documentation.*
