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

## Architecture Flow
```
Scanner → useBarcodeScanner hook → inventoryService.lookupByCode() → GET /api/v1/products/lookup?code=... → ProductService.lookupProductByCode() → DB index lookup → ProductResponse → addToCart()
```

## Files Modified
- `ProductRepository.java` — added `findByBarcodeAndTenantId()`
- `ProductService.java` — added `lookupProductByCode()`
- `ProductController.java` — added `GET /lookup` endpoint

## Files Created
- `V29__add_barcode_index.sql` — database index migration
