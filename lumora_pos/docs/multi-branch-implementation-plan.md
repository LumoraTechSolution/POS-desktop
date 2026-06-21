# Multi-Branch — Implementation Plan

A design doc for completing the multi-branch model. The schema is mostly in
place (per-branch stock, per-branch sales, per-branch POs, transfers, etc.)
but the **user → branch relationship doesn't exist**, so branch attribution is
self-asserted via a dropdown. This is the classic POS anti-pattern that leads
to inventory drift and unreconcilable shrinkage as soon as a tenant has more
than one branch.

This doc is forward-looking — none of the work below is implemented yet.

---

## Current state — what's right and what's missing

### What's already correct

| Area | Status |
|---|---|
| `stock_levels` keyed by `(product_id, branch_id)` | ✓ |
| `sales.branch_id` (V35) | ✓ |
| `purchase_orders.branch_id` | ✓ |
| `inventory_adjustments.branch_id` | ✓ |
| Stock transfers between branches | ✓ |
| Pessimistic locking on per-branch stock row at sale time | ✓ |
| `branches.is_default` flag for fallback | ✓ |

The schema is genuinely multi-tenant, multi-branch.

### What's missing

1. **No `user → branch` relationship.** Nothing on `users` ties a person to a
   branch. The system has no way to know which branch a cashier actually
   works at.
2. **Branch is self-asserted via a dropdown.** Every page that needs a branch
   (terminal, PO create, stock transfer, inventory adjustment) uses a free
   dropdown populated from `GET /branches`. Whatever the user picks is what
   gets recorded.
3. **No server-side enforcement.** `SaleService.createSale` accepts whatever
   `branchId` arrives in the request and resolves it. There's no check that
   the user is allowed to operate at that branch.
4. **Cash sessions have no branch.** `cash_sessions.branch_id` doesn't exist.
   A cashier can open a session, switch branches mid-shift via the dropdown,
   and the session silently spans both — variance is unattributable.
5. **Permissions are role-only, not branch-scoped.** A `MANAGER` is
   "manager of everything" — there's no way to make someone manager of just
   Branch A.
6. **Reports aggregate across all branches.** Sales report, profitability,
   employee performance — none filter by branch. A multi-branch owner can't
   ask "how did Branch A do this week" without ad-hoc SQL.

### Symptoms today

- A cashier in Branch A can pick Branch B from the dropdown and ring up sales
  there. Branch B's stock decrements, Branch A's cash drawer doesn't
  reconcile, audit trail is wrong.
- INVENTORY_MANAGER had no branch dropdown access at all until V35-era fixes
  (the `GET /branches` endpoint blocked them). Even now, they can pick *any*
  branch — there's no "their" branch.
- For single-branch tenants, none of this matters — the dropdown contains one
  option and there's nothing to get wrong. So the bug is invisible until a
  customer adds their second branch, and then it's catastrophic.

---

## Target architecture

Two patterns, usually combined in production POS systems (Square, Toast,
Lightspeed):

### Pattern A — User → Branch assignment

Every user is assigned to one **primary** branch and optionally a list of
**other branches they can work at**. The active branch is determined from
that assignment, not a free dropdown.

- `users.primary_branch_id` (FK to `branches.id`, nullable for backfill safety)
- `user_branches` (many-to-many) for staff who legitimately work multiple
  branches — a relief manager, a regional inventory manager, etc.
- On login, the active branch is set from `primary_branch_id`.
- Cashiers see no switcher — they can only operate at their assigned branch.
- Managers / admins / inventory managers get a switcher restricted to the
  branches they have access to.
- Server-side: every branch-scoped action (`createSale`, `createPO`,
  `adjustStock`, `transferStock`) validates that the request's `branchId`
  is in the caller's accessible-branches set.

### Pattern B — Terminal/Device → Branch (optional, future)

Each physical till or device is registered to a branch. The active branch is
determined by *which device the cashier is on*, not their identity. Useful
for shared-staff multi-branch operations (relief cashier walks between
branches and the till tells the system where they are).

- `devices` table (or use `localStorage` + a server-validated device token)
- The terminal sets its branch context from the device, not the user
- User must have access to the device's branch to log in there

This doc focuses on Pattern A. Pattern B is a future enhancement once Pattern
A is solid.

---

## Implementation plan

Phases are designed so each ships independently and the system stays usable
between them.

### Phase 1 — Data model & backfill (M)

**Goal:** introduce the `user → branch` relationship without changing any
behaviour yet. Existing dropdowns keep working; the new fields are populated
but ignored.

**Migration `V36__user_branch_assignment.sql`:**

```sql
-- Primary branch a user works at. Nullable for backfill safety; nullable
-- forever for super-admins who legitimately have no branch context.
ALTER TABLE users ADD COLUMN primary_branch_id UUID REFERENCES branches(id);
CREATE INDEX idx_users_primary_branch ON users(primary_branch_id);

-- Many-to-many for users who can operate at multiple branches (relief staff,
-- regional managers). The primary_branch_id should also appear here for
-- consistency; queries always read from this table for "can the user act at
-- branch X" checks.
CREATE TABLE user_branches (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id  UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, branch_id)
);
CREATE INDEX idx_user_branches_user ON user_branches(user_id);
CREATE INDEX idx_user_branches_branch ON user_branches(branch_id);

-- Backfill: every existing user gets assigned to their tenant's default
-- branch. Single-branch tenants are immediately correct. Multi-branch tenants
-- get a sensible default that admins can adjust per user.
INSERT INTO user_branches (user_id, branch_id, is_primary)
SELECT u.id, b.id, TRUE
FROM users u
JOIN branches b ON b.tenant_id = u.tenant_id AND b.is_default = TRUE;

UPDATE users u
SET primary_branch_id = (
    SELECT b.id FROM branches b
    WHERE b.tenant_id = u.tenant_id AND b.is_default = TRUE
);
```

**Entity changes:**
- `UserEntity` — add `@ManyToOne BranchEntity primaryBranch` + `@ManyToMany
  Set<BranchEntity> branches`.
- `BranchEntity` — no change.

**Service additions:**
- `UserService.getAccessibleBranchIds(userId)` — returns the set of branches
  a user can operate at. Single source of truth used by every authorization
  check downstream.

**No behaviour change yet.** Dropdowns still show every branch. Sales still
accept any `branchId`. This phase is just laying the foundation.

**Risk.** `users` is a hot table — adding a column with backfill on a large
tenant could lock it. Run the backfill in a separate migration step or off-hours
for prod. The backfill `UPDATE` is correlated subquery — fine for thousands
of users, may need batching for millions.

---

### Phase 2 — Server-side enforcement (M)

**Goal:** reject any request that tries to operate at a branch the user
doesn't have access to. Dropdowns still wide open at this point — we're just
making the server the source of truth.

**New helper:**

```java
// In a shared component, e.g. AuthorizationService.
public void assertBranchAccess(UUID userId, UUID branchId) {
    Set<UUID> accessible = userService.getAccessibleBranchIds(userId);
    if (!accessible.contains(branchId)) {
        throw new ForbiddenException("Not allowed to operate at this branch");
    }
}
```

**Wired into every branch-scoped action:**
- `SaleService.createSale` — assert before resolving branch
- `PurchaseOrderService.createPurchaseOrder` — assert against `request.branchId`
- `InventoryAdjustmentService.adjustStock` — assert against `request.branchId`
- `StockTransferService.createTransfer` — assert against BOTH source and
  destination branch IDs (the user must have access to at least one — usually
  source — to initiate)
- `ReturnService.restoreStock` — implicitly safe (uses `sale.branch`), but
  add a guard for the createReturn entry point

**Default branch fallback in `SaleService.createSale`** — when `branchId` is
null, fall back to the user's `primary_branch_id` instead of the tenant
default. Old behaviour (tenant default) becomes the second-tier fallback only
if the user has no primary branch (super-admin case).

**Risk.** Existing legacy sales / POs / adjustments still have whatever branch
they had — this phase only affects new requests. Nothing breaks for historical
data.

**Backfill safety.** Phase 1's backfill assigned every user to their tenant's
default branch. So the day this ships, no one is denied access — they all have
exactly one branch (the same one they were implicitly using before). Multi-
branch tenants get gradual ramp-up: admins start adding more branches to
specific users via the new admin UI (Phase 4).

---

### Phase 3 — POS terminal: drop the dropdown for cashiers (S)

**Goal:** cashiers stop seeing a branch picker. Their branch is fixed to
their primary assignment. Managers/admins keep the switcher but only see
their accessible branches.

**Frontend changes:**

`terminal/page.tsx`:
- Replace `branchService.getAllBranches()` with a new
  `branchService.getMyBranches()` endpoint that returns only the user's
  accessible branches. Saves a round-trip and prevents surprise.
- On mount, set `selectedBranch` from `user.primaryBranchId` (already in JWT
  / `useAuthStore`). If the user has only one accessible branch, hide the
  switcher entirely.
- Cashier role: switcher is disabled / hidden regardless of accessible
  branch count.

`POSHeader.tsx`:
- Conditionally render the branch dropdown — only when `accessibleBranches.length > 1`
  AND the user role is in `['ADMIN', 'MANAGER', 'INVENTORY_MANAGER']`.

**Backend additions:**
- `GET /api/v1/branches/me` — returns the caller's accessible branches.
  Replaces the catch-all `GET /branches` for the terminal use case.
- Existing `GET /branches` stays for admin pages that legitimately need the
  full list (e.g. user-branch assignment UI).

**Cash session change:** when starting a shift, persist `branch_id` on the
session so it can't span branches.

**Migration `V37__cash_session_branch.sql`:**

```sql
ALTER TABLE cash_sessions ADD COLUMN branch_id UUID REFERENCES branches(id);
CREATE INDEX idx_cash_sessions_branch ON cash_sessions(branch_id);
-- Backfill historical sessions to the default branch.
UPDATE cash_sessions cs
SET branch_id = (
    SELECT b.id FROM branches b
    WHERE b.tenant_id = cs.tenant_id AND b.is_default = TRUE
)
WHERE branch_id IS NULL;
```

`CashSessionService.startShift` requires a branch. The terminal sends its
active branch (which is now the user's primary or their selected one).
`SaleService.createSale` validates `request.branchId == session.branchId`
and rejects mismatches — this catches "cashier somehow has stale state from
another branch."

**Risk.** UX regression for managers who were used to seeing every branch in
the dropdown. Mitigation: if they have access to all branches (typical for
ADMIN), their `accessibleBranches` is the full list anyway, so behaviour is
identical.

---

### Phase 4 — Admin UI for branch assignment (M)

**Goal:** admins can manage who works where without SQL.

**New UI in `app/(dashboard)/employees/page.tsx`:**
- Each user row shows their branch chips: primary highlighted, secondaries
  in muted style.
- Per-user "Manage branches" modal: pick primary, toggle secondaries.
- Bulk action: "Assign all unassigned cashiers to Branch X."

**New endpoints:**
- `PUT /api/v1/users/{id}/branches` — replace the user's branch set
- `PATCH /api/v1/users/{id}/primary-branch` — change just the primary

Both ADMIN-only.

**Risk.** Re-assigning a cashier mid-shift is awkward — they have an open
cash session tied to the old branch. Solution: reject the re-assignment if
the user has an open cash session, or scope the change to "next session."

---

### Phase 5 — Branch-scoped reporting (M)

**Goal:** every report supports an optional branch filter, defaulted to the
user's primary branch for non-admin viewers.

**Backend:**
- Add `branchId` query param to every report endpoint:
  `/reports/sales`, `/reports/inventory-valuation`, `/reports/profitability`,
  `/reports/employee-performance`, `/reports/sold-items-by-supplier`,
  `/reports/stock-variance`.
- When `branchId` is present, predicate queries on it.
- When absent and viewer is not ADMIN, default to viewer's primary branch.
- When absent and viewer is ADMIN, aggregate across all branches (status quo).

**Frontend:**
- Branch picker in the reports page header next to the date-range picker.
- Hidden / disabled for users with one accessible branch.
- Persists in URL params so filtered views are shareable.

**Risk.** Some reports (`InventoryValuation`) are computed per
`stock_level` row, which already has a branch. Trivial to scope. Others
(`SoldItemsBySupplier`) aggregate via `sales` joined to `purchase_orders` —
need to decide whether the branch filter applies to sales side, PO side, or
both.

---

### Phase 6 — Branch-scoped permissions (L)

**Goal:** "manager of Branch A" — not just "MANAGER".

This is the deepest change and probably defer-able past v1.

- Roles become per-branch: `user_branches` gains a `role` column
  (or a separate `user_branch_roles` table for clarity).
- A user can be CASHIER at Branch A and MANAGER at Branch B.
- Authorization checks combine role and branch:
  `assertBranchAccess(userId, branchId, requiredRole)`.
- Refactors every `@PreAuthorize` to a custom `@HasBranchRole` annotation.

**Risk.** Big surface-area change to the auth model. Worth doing only if
real customer demand exists. Many SMBs will be fine with global roles + a
per-user branch list.

---

## Migration sequencing

| Phase | Blocks | Why |
|---|---|---|
| 1 | 2, 3, 4 | Phases 2–4 all read `user_branches`. |
| 2 | 3 | Phase 3's per-session branch enforcement assumes the server already validates branch access. |
| 3 | — | UX-only; ships independently after 2. |
| 4 | — | Admin UX, ships independently. |
| 5 | — | Reports are a separate slice — can ship anytime after 1. |
| 6 | — | Optional, future. |

A reasonable v1 ship cuts at Phase 3 (or Phase 4 if the admin-UI is needed
for customers to actually use the new model).

---

## Rejected alternatives

### "Just put a `default_branch_id` on `tenants` and let the dropdown control everything"

This is roughly what we have today. Rejected because the dropdown is
self-asserted — there's no defence against a cashier picking the wrong
branch, accidentally or maliciously. Tenant-default is fine as a *fallback*,
not as primary attribution.

### "Tie everything to terminals (Pattern B) and skip user assignment"

Workable but needs device registration, a device token, lifecycle management
(decommission a till, transfer a till between branches). More moving parts
than Pattern A, and Pattern A solves the immediate problem
(inventory drift) without device infrastructure. Pattern B is a good
follow-on.

### "Compute branch from the IP/network"

Cute, but breaks for cashiers using a tablet on a shared network, branches
that share a public IP via NAT, work-from-home back-office users (the
person doing reconciliation isn't even at a branch). Don't.

---

## Verification plan

Once Phase 3 is complete, the following should hold:

1. A CASHIER user with `primary_branch_id = A` logs in and sees no branch
   selector. Every sale they ring up is recorded against Branch A.
2. The same cashier cannot make a request to `POST /sales` with
   `branchId = B` even by direct API call — server returns 403.
3. A MANAGER with access to Branches A and B sees a switcher with both
   options. Picking A and ringing up a sale records it against A.
4. A MANAGER with access only to Branch A cannot pick Branch B (it's not in
   the list) and cannot bypass via API.
5. A cashier opening a cash session at Branch A cannot ring up a sale
   tagged to Branch B — `SaleService` rejects on the session-vs-sale branch
   mismatch.
6. Stock variance / profitability / sales reports default to the viewer's
   primary branch for non-admins; admins see all-branch aggregates.
7. Existing legacy data (sales, POs, adjustments created before Phase 1)
   continues to be queryable and remains attributed to whatever branch they
   were originally tagged with.

---

## Open questions

- **Cash session vs branch switching.** If a manager covers a cashier's till
  at Branch A while their own primary is Branch B, do they open a Branch A
  session? Probably yes — the manager temporarily acts as Branch A staff.
  But how do they get back to their own dashboard / reports view at Branch
  B without closing the session? Worth a short product conversation before
  Phase 3.
- **Super-admin context.** Tenant-level super-admins have no branch. Do they
  see a tenant-wide aggregated view by default, or are they forced to pick
  a branch to do anything? Suggest: aggregated view, with a switcher to
  scope to any single branch.
- **Re-org event** (a tenant adds a new branch). All existing users stay
  assigned to whatever they had. The admin must explicitly assign anyone to
  the new branch — no auto-migration. Document this in onboarding so an
  admin doesn't expect "create branch → cashiers see it" magic.
