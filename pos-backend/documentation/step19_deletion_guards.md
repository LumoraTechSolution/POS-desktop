# Step 19: Deletion Guards for Categories & Brands

## Overview

Implemented server-side deletion guards that prevent the deletion of Categories and Brands when products are still linked to them. This replaces raw PostgreSQL foreign key constraint errors with clean, user-friendly business exceptions.

## Date

2026-02-24

## Problem

Previously, attempting to delete a Category or Brand that had linked products would result in a raw database error:

```
ERROR: update or delete on table "categories" violates foreign key constraint
"fk_products_category" on table "products"
```

This is unreadable for end users and unhelpful for developers.

## Solution

Added a pre-deletion count check in both `CategoryService` and `BrandService`:

1. Before deleting, count how many products are linked using `ProductRepository`
2. If count > 0, throw a clean `BusinessException` with an actionable message

### Example Error Messages

- `"Cannot delete category 'Electronics': 12 product(s) are still linked to it. Please reassign or delete those products first."`
- `"Cannot delete brand 'Samsung': 8 product(s) are still linked to it. Please reassign or delete those products first."`

## Files Modified

| File                     | Change                                                                   |
| :----------------------- | :----------------------------------------------------------------------- |
| `ProductRepository.java` | Added `countByCategoryIdAndTenantId()` and `countByBrandIdAndTenantId()` |
| `CategoryService.java`   | Added `ProductRepository` injection + guard check in `deleteCategory()`  |
| `BrandService.java`      | Added `ProductRepository` injection + guard check in `deleteBrand()`     |

## Technical Details

- Guard checks are **tenant-scoped** — only counts products within the same tenant
- Uses Spring Data derived count queries (no custom SQL)
- Leverages existing DB indices on `category_id` and `brand_id` for fast lookups
- Guard runs AFTER the audit snapshot is captured but BEFORE the delete call

## Next Step

Phase 2, Step 4: Add composite database index `(tenant_id, created_at)` on `sales` table for reporting performance.
