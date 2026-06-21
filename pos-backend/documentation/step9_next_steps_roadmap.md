# Step 9: Immediate Next Steps & Roadmap

## Overview

Based on the audit completed in previous stages, we have identified specific technical debt to resolve before moving into the next functional phase (Sales Transaction Engine).

## 1. Immediate Technical Tasks (The "Fix-List")

### A. Backend & Database Hardening

- [ ] **Optimistic Locking**: Add a `@Version` field to the `BaseEntity` (affecting all entities). This is critical for high-concurrency POS environments to prevent race conditions during stock updates.
- [ ] **Method-Level Security**: Integrate `@PreAuthorize` annotations in all Controllers (e.g., `ROLE_ADMIN` check for deletions).
- [ ] **Flyway Migration Audit**: Perform a deep check of the schema integrity (pending `psql` path configuration or service startup).

### B. Frontend Polish

- [ ] **Product Table Actions**: Connect the "Edit" and "Delete" buttons in the `ProductTable.tsx` to the backend API.
- [ ] **Error Handling**: Implement global toast notifications for API error responses.

## 2. Functional Roadmap (Phase 1.4)

Once the foundation is hardened, we will move to the **Sales Transaction Engine**:

1.  **Cart Management Service**: Implementation of logic to hold multiple active carts and calculate subtotals.
2.  **Tax Calculation Engine**: Multi-tax support (GST, VAT, Sales Tax) based on location and category.
3.  **Basic Receipt Generation**: Formatting transaction data for thermal printers or email.
4.  **Payment Strategy Pattern**: Initial scaffolding for Stripe/Square/Cash payment handling.

## 3. Current Activity

- We are currently verifying the database schema and backend integrity.
- **Backend Service**: Currently offline (User requested to keep it down for now).
- **Database**: Active on port 5434.

---

**Approval Required**:
Please confirm if you would like me to start addressing the **Technical Tasks (A & B)** or move directly to the **Sales Transaction Engine (Phase 1.4)**.
