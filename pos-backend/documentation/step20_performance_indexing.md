# Step 20: Performance Indexing for Reporting

## Overview

Added a Flyway migration (`V11`) with 4 composite database indices to optimize reporting queries on the `sales` and `sale_items` tables.

## Date

2026-02-24

## Problem

The `sales` table only had a single index on `(tenant_id)`. Date-range queries used for daily/weekly/monthly reports (e.g., `findByTenantIdAndCreatedAtBetween`) would perform a sequential scan across all sales for the tenant — increasingly slow as data grows.

## Solution

Added composite indices that match the exact query patterns used in the system:

### Indices Created

| Index                       | Table        | Columns                        | Purpose                                              |
| :-------------------------- | :----------- | :----------------------------- | :--------------------------------------------------- |
| `idx_sales_tenant_created`  | `sales`      | `(tenant_id, created_at DESC)` | Daily summary, date-range reports                    |
| `idx_sales_tenant_payment`  | `sales`      | `(tenant_id, payment_method)`  | Payment breakdown analysis                           |
| `idx_sales_tenant_customer` | `sales`      | `(tenant_id, customer_id)`     | Customer purchase history (partial index, NULL-safe) |
| `idx_sale_items_product`    | `sale_items` | `(tenant_id, product_id)`      | Top-selling products report                          |

### Technical Notes

- `idx_sales_tenant_created` uses `DESC` ordering to optimize "latest first" queries
- `idx_sales_tenant_customer` is a **partial index** (`WHERE customer_id IS NOT NULL`) — saves disk space by excluding anonymous sales
- All indices use `IF NOT EXISTS` for idempotent execution

## Migration File

`backend/src/main/resources/db/migration/V11__reporting_performance_indices.sql`

## Next Step

Phase 2, Step 5: Fix N+1 query issue in `SaleService.mapItemToResponse()`.
