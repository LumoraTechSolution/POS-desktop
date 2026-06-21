# Lumora Enterprise POS — API Versioning & Testing Strategy

## 🌐 API Versioning Strategy (API-001)

Lumora follows a **URL-based versioning strategy** for its REST APIs to ensure high availability and backward compatibility for multi-tenant clients.

### 1. Structure
- **Current Version**: `/api/v1/**`
- **Future Version**: `/api/v2/**`

### 2. Implementation Rules
1. **Controller Versioning**: Controllers are scoped using `@RequestMapping("/api/v1/...")`. When a breaking change is required, a new version of the controller is created (e.g., `SaleControllerV2.java`) or the existing controller handles multiple version paths.
2. **Backward Compatibility**: `v1` remains active for a minimum of 18 months after a `v2` release to allow enterprise integrators (e.g., external ERPs/BI tools) time to migrate.
3. **Deprecation Notices**: Deprecated API versions return a `Warning` HTTP header:
   - `Warning: 299 - "API v1 is deprecated and will be removed on 2026-12-31. Use v2 instead."`

---

## 🧪 Integration Testing Strategy (Testing)

As identified in the technical audit, the system requires a robust integration test suite targeting the **Transactional Core** of the POS.

### 1. Objective
Achieve 40%+ code coverage for business-critical flows:
1. **Checkout**: Validating stock deduction, tax calculation, and loyalty point accrual in a single atomic transaction.
2. **Auth**: Testing role-based permissions and tenant isolation.
3. **Inventory Adjustment**: Ensuring real-time formula recalculation.

### 2. Technical Stack
- **JUnit 5 + Spring Boot Test**: Standard testing framework.
- **H2 / Testcontainers**: For lightweight, throwaway database instances during CI/CD.
- **Mockito**: For isolating external services like payment gateways or printer drivers.

### 3. Implementation Example (SaleService Integration)
- Create `SaleServiceIntegrationTest.java` in `src/test/java/com/lumora/pos/sales/service/`.
- Use `@SpringBootTest` with a real database context (H2).
- Create a test sale and verify that TWO branch-level stock records are correctly deducted and that the product total formula matches the summary.

---

## 🏗️ Phase 3 — Remaining Polish
- **[PERF-005] Completed**: Batch CSV Imports with N+1 resolution.
- **[SEC-007] Completed**: Hardened Actuator metrics with authentication requirements.
- **[FE-004] Completed**: Next.js Error Boundaries added to the dashboard.
- **[API-001] Strategy Documented**: This document serves as the architectural record for future versioning.
