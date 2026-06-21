# Backend Architecture — Step 1: Scaffolding

**Date**: February 17, 2026  
**Status**: ✅ Completed  
**Stack**: Spring Boot 3.3.7 | Java 17 | PostgreSQL 15 | Flyway

---

## Files Created

### Entry Point

| File                  | Path                            | Purpose                                          |
| --------------------- | ------------------------------- | ------------------------------------------------ |
| `PosApplication.java` | `src/main/java/com/lumora/pos/` | Spring Boot main class with `@EnableJpaAuditing` |

---

### Config (`com.lumora.pos.config/`)

| File                  | Purpose                                                                                                                                         |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `SecurityConfig.java` | Stateless JWT session management, CSRF disabled, BCrypt(12) password encoder, public endpoint whitelist (`/api/v1/auth/**`, `/actuator/health`) |
| `CorsConfig.java`     | CORS policy allowing the allowed origins for API consumers, configurable via `app.cors.allowed-origins`                                         |
| `AuditConfig.java`    | JPA `AuditorAware<UUID>` provider for `@CreatedBy` / `@LastModifiedBy` fields. Placeholder — will connect to SecurityContext in Step 2          |

---

### Common (`com.lumora.pos.common/`)

| File                             | Path                | Purpose                                                                                                                                                                |
| -------------------------------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BaseEntity.java`                | `common/entity/`    | Abstract `@MappedSuperclass` — all entities extend this. Fields: `id` (UUID), `tenantId` (UUID), `createdAt`, `updatedAt`, `createdBy`, `updatedBy`                    |
| `ApiResponse.java`               | `common/dto/`       | Generic response wrapper `ApiResponse<T>` with `success`, `message`, `data`, `errors`, `timestamp`. Factory methods: `success()`, `error()`                            |
| `PagedResponse.java`             | `common/dto/`       | Pagination wrapper with `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`                                                                      |
| `ResourceNotFoundException.java` | `common/exception/` | Thrown when entity not found → HTTP 404                                                                                                                                |
| `BusinessException.java`         | `common/exception/` | Thrown on business rule violations → HTTP 400                                                                                                                          |
| `GlobalExceptionHandler.java`    | `common/exception/` | `@RestControllerAdvice` — maps all exceptions to `ApiResponse` format. Handles: validation errors, bad credentials, access denied, constraint violations, generic 500s |

---

### Multi-Tenancy (`com.lumora.pos.tenant/`)

| File                 | Purpose                                                                                                                         |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `TenantContext.java` | `ThreadLocal<UUID>` holder for current tenant ID. Set on each request, cleared in `finally` block to prevent leaks              |
| `TenantFilter.java`  | `OncePerRequestFilter` (Order 1) — extracts tenant from `X-Tenant-ID` header. In Step 2, this will read from JWT claims instead |

---

### Application Config (`src/main/resources/`)

| File                   | Purpose                                                                                                                |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `application.yml`      | Main config: JPA (validate mode), Flyway, Jackson, JWT settings, Actuator, logging levels                              |
| `application-dev.yml`  | Dev profile: PostgreSQL `localhost:5432/lumora_pos`, HikariCP pool (10 max), verbose SQL logging, Flyway clean enabled |
| `application-prod.yml` | Prod profile: env-var driven DB URL, larger pool (20 max), restricted logging, Flyway clean disabled                   |

---

### Database Migration (`src/main/resources/db/migration/`)

| File                  | Purpose                                                                                                                                                                                                                                                         |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `V1__init_schema.sql` | Creates 6 tables: `tenants`, `permissions`, `roles`, `role_permissions`, `users`, `user_roles`, `audit_log`. Seeds demo tenant with 17 granular permissions and 4 system roles (ADMIN, MANAGER, CASHIER, INVENTORY_MANAGER) with correct permission assignments |

**Tables created:**

| Table              | Key Columns                                                                  | Indexes                                                                                           |
| ------------------ | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `tenants`          | id, name, domain, settings (JSONB), is_active                                | domain                                                                                            |
| `permissions`      | id, tenant_id, name, module, description                                     | tenant_id, (tenant_id + module)                                                                   |
| `roles`            | id, tenant_id, name, description, is_system                                  | tenant_id                                                                                         |
| `role_permissions` | role_id, permission_id                                                       | — (composite PK)                                                                                  |
| `users`            | id, tenant_id, email, password_hash, pin, first_name, last_name, is_active   | tenant_id, (tenant_id + email)                                                                    |
| `user_roles`       | user_id, role_id                                                             | — (composite PK)                                                                                  |
| `audit_log`        | id, tenant_id, user_id, action, entity_type, entity_id, old_value, new_value | tenant_id, (tenant_id + entity_type + entity_id), (tenant_id + user_id), (tenant_id + created_at) |

---

### Test (`src/test/`)

| File                       | Purpose                                                |
| -------------------------- | ------------------------------------------------------ |
| `PosApplicationTests.java` | Basic context loading test                             |
| `application-test.yml`     | Test profile: `ddl-auto: create-drop`, Flyway disabled |

---

### Other

| File         | Purpose                                                                                                                                                                                       |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `.gitignore` | Ignores `target/`, IDE files, `.env`, logs                                                                                                                                                    |
| `pom.xml`    | Maven config with dependencies: spring-boot-starter-web, data-jpa, security, validation, actuator, postgresql, flyway-core, flyway-database-postgresql, jjwt (0.12.6), lombok, devtools, test |

---

## Key Design Decisions

1. **Multi-tenancy**: Shared database with `tenant_id` discriminator column on every table. `TenantContext` (ThreadLocal) + `TenantFilter` (servlet filter) ensure automatic scoping.
2. **BaseEntity**: Every domain entity extends `BaseEntity` for consistent UUID PKs, tenant scoping, and audit trails.
3. **ApiResponse wrapper**: All endpoints return `ApiResponse<T>` for consistent JSON structure.
4. **Flyway validate mode**: Schema changes MUST go through migration scripts — Hibernate cannot auto-modify the schema.
5. **BCrypt(12)**: Cost factor of 12 for password hashing (balances security vs. speed).
