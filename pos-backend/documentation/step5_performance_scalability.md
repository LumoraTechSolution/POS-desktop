# Step 5: Performance & Scalability Review

## Objective

To identify potential bottlenecks, optimize database interactions, and ensure the system can handle enterprise-level data volumes and concurrent checkout operations.

## Activities

- [x] Audit Database Indexing Strategy (Check migration scripts).
- [x] Scan for N+1 Query issues in Sales and Inventory modules.
- [x] Evaluate POS Checkout Latency (Frontend rendering vs Backend processing).
- [x] Review Pagination implementation for large datasets (Products, Sales History).
- [x] Check for Caching opportunities (Product catalogs).

## Findings

### 1. Database Indexing

- **Tenant Isolation**: Indices are correctly implemented for `tenant_id` across all major tables (`products`, `sales`, `sale_items`, `customers`). This is vital for query performance in a multi-tenant environment.
- **Search Optimization**: `sku` in the `products` table and `invoice_number` in `sales` are indexed, facilitating fast lookups during transactions.
- **Missing Indices**: The `sales` table is queried by `created_at` in the `getDailySummary` method, but there is no index on `created_at`. As data grows, this will degrade performance for reporting.

### 2. N+1 Query Analysis

- **Sale Response**: In `SaleService.mapItemToResponse`, a call is made to `productRepository.findById` for _every_ item in the sale. For a large cart, this results in multiple database roundtrips.
- **Category/Brand Lazy Loading**: In `ProductService.mapToResponse`, accessing `product.getCategory().getName()` triggers a lazy load if not already in the session. When fetching a page of 50 products, this can lead to 100 extra queries.

### 3. POS Latency & Frontend Performance

- **Terminal Filtering**: The frontend performs client-side filtering on a locally cached list of 50 products. This ensures nearly zero-latency search results for the cashier.
- **Checkout Atomicity**: The backend handles stock deduction and record creation in a single transaction. Optimistic locking prevents long-running DB locks, keeping the API responsive.

### 4. Data Volume Handling

- **Pagination**: Implemented for `getAllProducts` and `getAllCustomers` (default size 10-50).
- **Infinite Scroll Opportunity**: The POS product grid currently fetches 50 items. This should be converted to an infinite scroll or virtualized list to support catalogs of 10,000+ items.

### 5. Caching Strategy

- **TanStack Query**: The frontend uses `TanStack Query` for effective client-side caching of products and customers, reducing redundant API hits.
- **Redis Opportunity**: No backend caching (e.g., Redis) is currently used for frequent product catalog lookups. This will be necessary when scaling to hundreds of concurrent tenants.

## Recommendations

1.  **Add Composite Indices**: Add an index for `(tenant_id, created_at)` on the `sales` table to optimize summaries and history reports.
2.  **Fix N+1 in Sales Summary**: Use `@EntityGraph` or a JPQL JOIN FETCH in `SaleRepository` to fetch items and their names in a single query.
3.  **Implement Server-Side Product Search**: While client-side search is fast, it fails for large catalogs. Transition to server-side search with debounce for the POS terminal.

## Status

Completed
