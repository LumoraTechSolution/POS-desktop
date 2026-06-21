# Step 34: Fix Branch Query Error in InventoryAdjustmentModal

## Date

2026-03-03

## Issue

Terminal warning:

```
[["branches"]]: No queryFn was passed as an option, and no default queryFn was found.
```

The branch dropdown in the Adjust and Transfer tabs was not loading any data.

## Root Cause

Method name mismatch between the service and the component:

- **Service exports:** `branchService.getAllBranches`
- **Component called:** `branchService.getBranches` (doesn't exist → `undefined`)

React Query received `undefined` as its `queryFn` and threw the error.

## Fix Applied

```diff
-queryFn: branchService.getBranches
+queryFn: branchService.getAllBranches
```

## Affected File

- `src/components/inventory/InventoryAdjustmentModal.tsx` (line 72)

## Verification

- ✅ Hot-reload picks up the change automatically
- ✅ `getAllBranches` confirmed as the correct method name in `branchService.ts`

## Risk Assessment

- **Risk Level:** Low
- **Regression Risk:** None — single line fix, corrects a broken function reference
