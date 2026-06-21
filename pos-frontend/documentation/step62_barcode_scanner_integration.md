# Step 62: Barcode Scanner → Cart Integration (Server-Side Lookup)

## Overview
Fixed a critical architectural flaw where the POS terminal barcode scanner only searched against the first 50 pre-fetched products (client-side filtering). Replaced with a dedicated server-side barcode/SKU lookup API call for instant resolution across unlimited products.

## Problem
The previous implementation fetched only 50 products via `inventoryService.getProducts(0, 50)` and then used `Array.find()` to match barcodes client-side. Any product beyond the first page would silently fail with "Barcode not found" even though it existed in the database.

## Key Changes

### Frontend
1. **`inventoryService.ts`** — Added `lookupByCode(code)` service method hitting `GET /api/v1/products/lookup?code={code}`.
2. **`terminal/page.tsx`** — Refactored `useBarcodeScanner` to:
   - Call the server-side API instead of filtering local products array
   - Added duplicate scan protection (500ms debounce guard via `useRef`)
   - Uses async/await pattern with proper error handling
   - Added `useRef` to React imports

## Architecture Flow
```
Scanner → useBarcodeScanner hook → inventoryService.lookupByCode() → GET /api/v1/products/lookup → addToCart()
```

## Files Modified
- `inventoryService.ts` — added `lookupByCode()` method
- `terminal/page.tsx` — refactored barcode scanner integration
