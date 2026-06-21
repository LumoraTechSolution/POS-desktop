# Step 48: Currency Change — USD to LKR (Sri Lankan Rupee)

## Date: 2026-03-24

## Objective
Change the system's primary currency from USD ($) to Sri Lankan Rupee (LKR, Rs.) to target local Sri Lankan businesses. USD is kept as a secondary supported option.

---

## Architecture Decision
Instead of doing a simple find-and-replace of `$` symbols, we implemented a **centralized currency configuration** system:

### `lib/utils.ts` — Currency Core
```typescript
export const CURRENCY = {
  code: 'LKR' as const,
  symbol: 'Rs.',
  locale: 'en-LK',
} as const;
```

- `formatCurrency(amount, 'LKR')` → Uses `Intl.NumberFormat('en-LK', ...)` for locale-correct formatting
- `formatCurrency(amount, 'USD')` → Falls back to `Intl.NumberFormat('en-US', ...)` for USD
- `fc(amount)` → Shorthand that always uses the primary currency
- `CURRENCY.symbol` → Used by all UI components for inline display

### Why This Approach?
- **Single source of truth**: To switch the entire app to USD, change just ONE constant.
- **Multi-currency ready**: The `formatCurrency()` function already supports any Intl currency code.
- **Future SaaS support**: When the Super Admin dashboard is built, tenant-specific currency preferences can override `CURRENCY.code` per session.

---

## Files Modified

### Frontend (10 files)
| File | Changes |
|---|---|
| `lib/utils.ts` | Added `CURRENCY` constant, `fc()` shorthand, changed default from USD to LKR |
| `components/pos/CartItemCard.tsx` | `$` → `{CURRENCY.symbol}` |
| `components/pos/CheckoutPanel.tsx` | 3× `$` → `{CURRENCY.symbol}` (subtotal, tax, total) |
| `components/pos/ProductGrid.tsx` | `$` → `{CURRENCY.symbol}` (price display) |
| `components/pos/Receipt.tsx` | 5× `$` → `{CURRENCY.symbol}`, updated store address to Sri Lanka |
| `components/pos/ShiftSummary.tsx` | 8× `$` → `{CURRENCY.symbol}` (all amounts) |
| `components/pos/ReturnModal.tsx` | 2× `$` → `{CURRENCY.symbol}` |
| `components/pos/ExchangeModal.tsx` | 14× `$` → `{CURRENCY.symbol}` (all prices, credits, balances) |
| `app/(dashboard)/inventory/purchase-orders/page.tsx` | 3× `$` → `{CURRENCY.symbol}` |
| `app/(dashboard)/inventory/purchase-orders/CreatePOModal.tsx` | 2× `$` → `{CURRENCY.symbol}` |

### Backend (1 file)
| File | Changes |
|---|---|
| `sales/service/SaleService.java` | Updated loyalty comment from `$10` to `LKR 10` |

### Receipt Template
- Default store address updated: `City, State, 12345` → `Colombo, Sri Lanka`
- Default phone updated: `+1 (555) 123-4567` → `+94 11 234 5678`

---

## Testing Checklist
- [ ] POS Terminal: Product prices show "Rs."
- [ ] POS Terminal: Cart item totals show "Rs."
- [ ] POS Terminal: Checkout panel subtotal/tax/total show "Rs."
- [ ] Receipt: All amounts show "Rs."
- [ ] Shift Summary: All payment breakdowns show "Rs."
- [ ] Returns: Unit price and total return value show "Rs."
- [ ] Exchange: All credits, costs, and balances show "Rs."
- [ ] Purchase Orders: PO total amounts show "Rs."
- [ ] Purchase Orders: Create modal line item totals show "Rs."
- [ ] Dashboard: `formatCurrency()` calls now output "LKR" formatted values
- [ ] Reports: All `formatCurrency()` calls render LKR

---

## Multi-Currency Future Plan
When the Super Admin system is built, the currency will become tenant-configurable:
1. `TenantConfiguration` entity will have a `currency_code` field (default: `LKR`)
2. On login, the API returns the tenant's currency code
3. Frontend stores it in Zustand and overrides `CURRENCY.code` per session
4. All existing `{CURRENCY.symbol}` and `formatCurrency()` calls automatically adapt
