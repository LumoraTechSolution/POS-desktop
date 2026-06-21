# Step 25: Server-Side Search, Filter & Sort - Backend

**Date:** 2026-02-24

## Overview

Enhanced backend API endpoints to support server-side search, filtering, and sorting for products, categories, and brands.

## Files Modified

| File                        | Change                                                             |
| --------------------------- | ------------------------------------------------------------------ |
| `ProductRepository.java`    | Added `JpaSpecificationExecutor` for dynamic queries               |
| `ProductSpecification.java` | New — builds WHERE clauses for search/filters                      |
| `ProductController.java`    | Accepts `search`, `categoryId`, `brandId`, `isActive` query params |
| `ProductService.java`       | Uses `ProductSpecification.withFilters()` for queries              |
| `CategoryRepository.java`   | Added `searchByName()` JPQL query                                  |
| `BrandRepository.java`      | Added `searchByName()` JPQL query                                  |
| `CategoryService.java`      | `getAllCategories(search)` routes to search                        |
| `BrandService.java`         | `getAllBrands(search)` routes to search                            |
| `CategoryController.java`   | Accepts `search` query param                                       |
| `BrandController.java`      | Accepts `search` query param                                       |

## API Endpoints Updated

```
GET /api/v1/products?search=&categoryId=&brandId=&isActive=&sort=basePrice,desc&page=0&size=20
GET /api/v1/categories?search=
GET /api/v1/brands?search=
```
