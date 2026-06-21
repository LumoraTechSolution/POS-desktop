# Step 2: Authentication & Authorization (Backend)

**Status**: ✅ Completed  
**Objective**: Implement a secure, multi-tenant POS authentication system using JWT (HS256) for access and opaque tokens for refresh, supporting both standard email/password and fast 4-digit PIN access.

---

## Files Created/Modified

### Security Configuration

| File                           | Path                                     | Purpose                                                                              |
| ------------------------------ | ---------------------------------------- | ------------------------------------------------------------------------------------ |
| `SecurityConfig.java`          | `src/main/java/com/lumora/pos/config/`   | Configured stateless session management, CSRF disabling, and JWT filter positioning. |
| `JwtAuthenticationFilter.java` | `src/main/java/com/lumora/pos/security/` | Intercepts requests to validate tokens and set the security context.                 |

### Auth Logic

| File                  | Path                                            | Purpose                                                                           |
| --------------------- | ----------------------------------------------- | --------------------------------------------------------------------------------- |
| `AuthService.java`    | `src/main/java/com/lumora/pos/auth/service/`    | Handles user authentication, token generation (Access/Refresh), and user context. |
| `JwtProvider.java`    | `src/main/java/com/lumora/pos/security/`        | Core logic for generating and parsing HS256 tokens.                               |
| `AuthController.java` | `src/main/java/com/lumora/pos/auth/controller/` | REST endpoints for `/login`, `/refresh`, and `/logout`.                           |

### Database Schema

| File                       | Path                               | Purpose                                                         |
| -------------------------- | ---------------------------------- | --------------------------------------------------------------- |
| `V2__auth_refinements.sql` | `src/main/resources/db/migration/` | Added tables for users, roles, permissions, and refresh tokens. |
| `V3__demo_data.sql`        | `src/main/resources/db/migration/` | Seeded demo admin and cashier accounts for testing.             |

---

## Key Features

1. **JWT System**: Access tokens (1h) and persistent refresh tokens (7d).
2. **Multi-Tenancy**: Every auth operation is scoped by `tenantId`.
3. **Role-Based Access (RBAC)**: Supported roles: `ADMIN`, `MANAGER`, `CASHIER`.
4. **Fast PIN Login**: Specialized endpoint for terminal-only access without passwords.

---

## Security Decisions

- **Statelessness**: No HTTP sessions used; all state is in the JWT.
- **Auditing**: Integrated with Spring Data `@CreatedBy` to track user actions automatically.
