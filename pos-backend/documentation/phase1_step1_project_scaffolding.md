# Phase 1 — Step 1: Project Scaffolding

**Date**: February 17, 2026  
**Status**: ✅ Completed

---

## Objective

Initialize the development environment with Spring Boot backend and PostgreSQL database.

---

## Steps

### 1.1 — Spring Boot Backend Initialization

Create a Spring Boot 3.3+ project with the following configuration:

| Setting      | Value            |
| ------------ | ---------------- |
| Build Tool   | Maven            |
| Java Version | 17+              |
| Spring Boot  | 3.3.x            |
| Group        | `com.lumora`     |
| Artifact     | `pos-backend`    |
| Package      | `com.lumora.pos` |

**Dependencies**:

- `spring-boot-starter-web` — REST APIs
- `spring-boot-starter-data-jpa` — JPA/Hibernate
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-validation` — Bean validation
- `spring-boot-starter-actuator` — Health checks & monitoring
- `postgresql` — PostgreSQL JDBC driver
- `flyway-core` + `flyway-database-postgresql` — DB migrations
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` — JWT token handling
- `lombok` — Boilerplate reduction
- `spring-boot-starter-test` — Testing
- `spring-boot-starter-amqp` — RabbitMQ (prepared, not active yet)
- `spring-boot-starter-data-redis` — Redis (prepared, not active yet)

**Directory structure**:

```
backend/
├── pom.xml
├── src/main/java/com/lumora/pos/
│   ├── PosApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── CorsConfig.java
│   │   ├── JwtConfig.java
│   │   └── AuditConfig.java
│   ├── common/
│   │   ├── entity/BaseEntity.java        # id, tenantId, createdAt, updatedAt, createdBy
│   │   ├── dto/ApiResponse.java          # Standard response wrapper
│   │   ├── dto/PagedResponse.java        # Pagination wrapper
│   │   ├── exception/GlobalExceptionHandler.java
│   │   ├── exception/ResourceNotFoundException.java
│   │   ├── exception/BusinessException.java
│   │   └── audit/AuditListener.java
│   └── tenant/
│       ├── TenantContext.java            # ThreadLocal tenant holder
│       └── TenantFilter.java            # Servlet filter to extract tenant from JWT
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/
│       └── V1__init_schema.sql           # Initial schema (tenants, users, roles, permissions)
└── src/test/java/com/lumora/pos/
    └── PosApplicationTests.java
```

### 1.3 — PostgreSQL + Flyway Initial Migration

**`V1__init_schema.sql`** will create the foundational tables:

- `tenants` — multi-tenant root
- `roles` — RBAC roles
- `permissions` — granular permissions
- `role_permissions` — many-to-many
- `users` — system users scoped to tenant
- `user_roles` — many-to-many

All tables include `tenant_id`, `created_at`, `updated_at` audit columns.

### 1.4 — Docker Compose

```yaml
# docker-compose.yml — local development
services:
  postgres: # PostgreSQL 15, port 5432
  redis: # Redis 7, port 6379
  backend: # Spring Boot, port 8080
```

### 1.5 — Documentation

Save this file as the Step 1 documentation record.

---

## What Gets Delivered

After this step you will have:

1. ✅ A compiling Spring Boot backend with security, JPA, Flyway, and modular package structure
2. ✅ PostgreSQL database with initial schema (tenants, users, roles, permissions)
3. ✅ Docker Compose for one-command local startup
4. ✅ All code compiles and runs locally
