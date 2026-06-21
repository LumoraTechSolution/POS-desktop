package com.lumora.pos.inventory.repository;

import com.lumora.pos.inventory.entity.ProductEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dynamic query builder for Product search, filter, and sort.
 * Uses Spring Data JPA Specifications (Criteria API) to construct
 * type-safe, composable WHERE clauses at runtime.
 */
public final class ProductSpecification {

    private ProductSpecification() {
        // Utility class
    }

    /**
     * Builds a dynamic Specification from optional filter parameters.
     * Only non-null parameters are added to the WHERE clause.
     *
     * Note: ProductEntity uses ManyToOne relationships for category and brand,
     * so we navigate the relationship path (e.g., root.get("category").get("id"))
     * rather than using a simple field name.
     */
    public static Specification<ProductEntity> withFilters(
            UUID tenantId,
            String search,
            UUID categoryId,
            UUID brandId,
            Boolean isActive) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by tenant (mandatory for multi-tenant isolation)
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            // Search: match name OR sku (case-insensitive)
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern)));
            }

            // Filter by category (navigate ManyToOne relationship)
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            // Filter by brand (navigate ManyToOne relationship)
            if (brandId != null) {
                predicates.add(cb.equal(root.get("brand").get("id"), brandId));
            }

            // Filter by active status
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
