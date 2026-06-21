# Step 33: Fix Invalid `PlusMinus` Icon Import in InventoryAdjustmentModal

## Date

2026-03-03

## Issue

**React render crash** in `InventoryAdjustmentModal`:

```
Error: Element type is invalid: expected a string (for built-in components) or a class/function
(for composite components) but got: undefined.
Check the render method of `InventoryAdjustmentModal`.
```

## Root Cause

The component imported `PlusMinus` from `lucide-react`, but **this icon does not exist** in the library. The import resolved to `undefined`, which React then tried to render as a component — causing the crash.

The icon was used in 3 places:

1. **Line 33** — Import statement
2. **Line 142** — Modal header icon
3. **Line 156** — Tab trigger icon for "Adjust" tab

## Fix Applied

Replaced `PlusMinus` with `PackagePlus`, a valid `lucide-react` icon that semantically fits "inventory management."

### Diff

```diff
 import {
   History,
   ArrowLeftRight,
-  PlusMinus,
+  PackagePlus,
   Loader2,
   ...
 } from "lucide-react";

- <PlusMinus size={20} />
+ <PackagePlus size={20} />

- <PlusMinus size={16} /> Adjust
+ <PackagePlus size={16} /> Adjust
```

## Affected File

- `src/components/inventory/InventoryAdjustmentModal.tsx`

## Verification

- ✅ No remaining `PlusMinus` references in frontend codebase
- ✅ `PackagePlus` confirmed to exist in installed `lucide-react` version
- ✅ Dev server hot-reload should pick up fix automatically

## Risk Assessment

- **Risk Level:** Low
- **Regression Risk:** None — purely a cosmetic icon swap, no logic changes
