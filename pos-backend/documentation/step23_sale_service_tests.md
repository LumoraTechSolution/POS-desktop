# Step 23: Core Unit Tests for SaleService

## Overview

Created comprehensive unit tests for `SaleService` — the most financially critical service in the POS system. These tests protect the core checkout math from regressions.

## Date

2026-02-24

## Test Coverage

### 18 Test Cases in 6 Categories

| Category              | Tests | What's Verified                                                  |
| :-------------------- | :---- | :--------------------------------------------------------------- |
| **Single Item Sales** | 3     | No discount, with discount, fractional quantities                |
| **Multi-Item Sales**  | 1     | Correct aggregation of totals across multiple items              |
| **Stock Management**  | 3     | Stock deduction, insufficient stock error, missing product error |
| **Payment Info**      | 2     | Payment method persistence, invoice number generation            |
| **Audit Logging**     | 2     | Audit fires on success, does NOT fire on failure                 |
| **Edge Cases**        | 3     | Zero discount, single quantity, exact stock match                |

### Financial Math Verified

The tests verify this exact formula for each item:

```
subtotal     = unitPrice × quantity
tax          = (subtotal - discount) × 0.10
itemTotal    = subtotal - discount + tax
```

And for the sale totals:

```
totalAmount  = Σ(subtotals)
taxAmount    = Σ(taxes)
discountAmt  = Σ(discounts)
netAmount    = totalAmount - discountAmount + taxAmount
```

### Example Test Case

```
Given: 2× Widget A at $100, 1× Widget B at $200 with $10 discount
Item 1: subtotal=200, tax=20.00, total=220.00
Item 2: subtotal=200, discount=10, tax=19.00, total=209.00
Sale:   total=400, discount=10, tax=39, net=429.00
```

## Technical Details

- Uses **Mockito** for isolation (no database, no Spring context needed)
- Uses **AssertJ** `isEqualByComparingTo` for BigDecimal comparisons (ignores scale differences)
- `ArgumentCaptor` used to verify stock deduction values
- `TestUtils` for TenantContext and SecurityContext setup

## File Created

- `backend/src/test/java/com/lumora/pos/sales/service/SaleServiceTest.java`

## Next Step

Phase 3, Step 8: Refactor TerminalPage.tsx (frontend component decomposition).
