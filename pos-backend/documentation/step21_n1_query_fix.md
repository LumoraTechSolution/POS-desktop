# Step 21: Fix N+1 Query in SaleService

## Overview

Fixed the N+1 database query problem in `SaleService.mapToResponse()` by batch-fetching all product names in a single query instead of making individual database calls for each sale item.

## Date

2026-02-24

## Problem

The `mapItemToResponse()` method was calling `productRepository.findById(item.getProductId())` for **each sale item individually**:

```
Receipt with 10 items = 10 separate SELECT queries to the products table
Receipt with 50 items = 50 separate SELECT queries
```

This is the classic "N+1 query problem" — 1 query for the sale + N queries for each item's product name.

## Solution

Refactored `mapToResponse()` to:

1. **Collect** all unique product IDs from the sale items
2. **Batch-fetch** all products in **one query** using `productRepository.findAllById(productIds)`
3. **Build** an in-memory `Map<UUID, String>` for product name lookup
4. **Pass** the map to `mapItemToResponse()` for O(1) lookups

### Before (N+1 queries)

```java
private SaleResponse.SaleItemResponse mapItemToResponse(SaleItemEntity item) {
    String productName = productRepository.findById(item.getProductId())  // ← DB call per item!
            .map(ProductEntity::getName)
            .orElse("Unknown Product");
    ...
}
```

### After (1 batch query)

```java
private SaleResponse mapToResponse(SaleEntity sale) {
    Set<UUID> productIds = sale.getItems().stream()
            .map(SaleItemEntity::getProductId)
            .collect(Collectors.toSet());

    Map<UUID, String> productNameMap = productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(ProductEntity::getId, ProductEntity::getName));
    ...
}
```

## Performance Impact

| Receipt Size | Before (queries) | After (queries) | Improvement |
| :----------- | :--------------- | :-------------- | :---------- |
| 5 items      | 6                | 2               | 3x fewer    |
| 10 items     | 11               | 2               | 5.5x fewer  |
| 50 items     | 51               | 2               | 25.5x fewer |

## File Modified

- `backend/src/main/java/com/lumora/pos/sales/service/SaleService.java`

## Next Step

Phase 3, Step 6: Setup Testing Infrastructure (JUnit + Vitest).
