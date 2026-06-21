# Step 50 — Step 6: Backend Limits Validation (Locations, Users, Products)

## Summary
To enforce the tiered subscription capacity limits defined by the `TenantConfigurationEntity`, we need to check the current count of resources against the tenant's maximum allowed limits prior to creation. If the limit has been reached, the backend should throw a `403/400` logic exception indicating the plan needs to be upgraded.

### Updates Applied

#### 1. `BranchService.java` (Locations)
- **Injection:** Injected `TenantConfigurationRepository`.
- **Logic Added:** Inside `createBranch(...)`, we execute `branchRepository.countByTenantId(tenantId)`. 
- **Guard:** If `currentCount >= config.getMaxLocations()`, throw a `BusinessException` halting branch creation.

#### 2. `UserManagementService.java` (Users)
- **Repository Support:** Added `countByTenantId(UUID)` to `UserRepository`.
- **Injection:** Injected `TenantConfigurationRepository`.
- **Logic Added:** Inside `createUser(...)`, we count existing users. 
- **Guard:** If `currentCount >= config.getMaxUsers()`, throw a `BusinessException` halting user creation.

#### 3. `ProductService.java` (Products)
- **Injection:** Injected `TenantConfigurationRepository`.
- **Logic Added (Single Creation):** Inside `createProduct(...)`, we measure the global catalog stock using `productRepository.countByTenantId(tenantId)`. If `currentCount >= config.getMaxProducts()`, creation is rejected.
- **Logic Added (CSV Import):** Inside `importProductsFromCsv(...)`, before saving a *new* product entity parsed from the CSV, we count the current products. If the limit is reached mid-import, the entire transaction correctly fails with an informative exception, preventing partial breaches.

## Resilience Note
By placing the limit checks directly within the `@Transactional` service layer rather than the controllers, we ensure that bulk imports or any internal system actions still rigorously enforce the SaaS subscription limits. 
