# Inventory Management Logic Rationale

## Overview
This document explains the coexistience of two distinct inventory management methods within the Lumora POS system: **Formal Operational Workflows** (Purchase Orders & Stock Transfers) and **Direct Inventory Adjustments** (Product Management).

## 1. Formal Operational Workflows
*Located in: Inventory > Purchase Orders, Inventory > Stock Transfers*

### Purpose
These modules handle the **planned and tracked movement** of goods into or within the enterprise.

### Key Characteristics
1. **Multi-Step Lifecycle**: Transactions move through states (e.g., `PENDING` → `IN_TRANSIT` → `COMPLETED`).
2. **Accountability**: Tracks which user initiated the move and which user received it.
3. **External Integration**: Purchase Orders link to **Suppliers** and financial records.
4. **Distance & Time**: Account for the period where stock is "in limbo" (not at source, not yet at destination).
5. **Data Integrity**: Stock is only deducted/added when the workflow is finalized, preventing "phantom stock" during transit.

### When to Use
- Buying new stock from a vendor.
- Moving items from a central warehouse to a retail branch.
- Any movement requiring an official record for accounting or loss prevention.

---

## 2. Direct Inventory Adjustments
*Located in: Products > Inventory Management Option*

### Purpose
This module handles **immediate corrections** and real-time stock state management for a single location.

### Key Characteristics
1. **Instant Execution**: Stock levels are updated immediately upon submission.
2. **Non-Workflow Based**: No "transit" or "reception" phases.
3. **Correction Centric**: Primarily used for reconciling system data with physical reality.
4. **Simplicity**: High speed, low friction for minor corrections.

### When to Use
- **Cycle Counting**: Correcting discrepancies found during a shelf count.
- **Damage/Wastage**: Removing items that were broken or expired on-site.
- **Initial Setup**: Seeding initial stock levels when first launching a branch.

---

## Comparative Matrix

| Feature | Formal Workflows (PO/Transfer) | Direct Adjustments |
| :--- | :--- | :--- |
| **Logic** | Document-based Workflow | Item-based Correction |
| **Audit Detail** | High (From/To, User A/User B, Status) | Medium (User, Reason Code) |
| **Financial Impact** | Direct (COGS, Accounts Payable) | Indirect (Inventory write-offs) |
| **Speed** | Multiple steps / Days | Single step / Instant |
| **Collaboration** | Required (Sender & Receiver) | Single user |

## Recommendation
Both systems must coexist to support an enterprise-grade environment. 
- **Removing Workflows** would leave the business blind to where stock is "in transit" and prevent supplier management.
- **Removing Direct Adjustments** would make simple inventory corrections unnecessarily tedious and slow down shop-floor efficiency.
