# Step 62: Barcode Scanner → Cart Integration (Server-Side Lookup)

## Overview
Fixed a critical architectural flaw where the POS terminal barcode scanner only searched against the first 50 pre-fetched products (client-side filtering). Replaced with a dedicated server-side barcode/SKU lookup API endpoint backed by a database index for instant resolution across unlimited products.

## Problem
The previous implementation fetched only 50 products via `inventoryService.getProducts(0, 50)` and then used `Array.find()` to match barcodes client-side. Any product beyond the first page would silently fail with "Barcode not found" even though it existed in the database.

## Key Changes

### Database
1. **`V29__add_barcode_index.sql`** — Added composite index `(barcode, tenant_id)` and unique constraint to prevent duplicate barcodes per tenant.

### Backend
2. **`ProductRepository.java`** — Added `findByBarcodeAndTenantId(String barcode, UUID tenantId)` method.
3. **`ProductService.java`** — Added `lookupProductByCode(String code)` method that tries barcode first, falls back to SKU, and throws `ResourceNotFoundException` on miss.
4. **`ProductController.java`** — Added `GET /api/v1/products/lookup?code={code}` endpoint secured for ADMIN, MANAGER, and CASHIER roles.

### Frontend
5. **`inventoryService.ts`** — Added `lookupByCode(code)` service method hitting the new endpoint.
6. **`terminal/page.tsx`** — Refactored `useBarcodeScanner` to:
   - Call the server-side API instead of filtering local products
   - Added duplicate scan protection (500ms debounce guard)
   - Uses async/await pattern with proper error handling

## Architecture Flow
```
Scanner → useBarcodeScanner hook → inventoryService.lookupByCode() → GET /api/v1/products/lookup?code=... → ProductService.lookupProductByCode() → DB index lookup → ProductResponse → addToCart()
```

## Impact
- **Scale**: Works with unlimited products (was limited to 50)
- **Accuracy**: Always returns real-time stock data from database
- **Performance**: O(log N) index lookup instead of full table scan
- **Security**: Only the matched product is returned, not the entire catalog
- **Reliability**: Duplicate scan protection prevents double-fire issues
