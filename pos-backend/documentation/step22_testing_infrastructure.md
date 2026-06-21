# Step 22: Testing Infrastructure Setup

## Overview

Set up the testing infrastructure to enable unit testing of backend services without requiring a real PostgreSQL database. Tests run against an in-memory H2 database for speed and isolation.

## Date

2026-02-24

## What Was Created/Modified

### 1. H2 Dependency Added (`pom.xml`)

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

This allows tests to run with an in-memory database — no Docker or PostgreSQL required.

### 2. Test Configuration (`application-test.yml`)

Updated to use H2 in **PostgreSQL compatibility mode**:

| Setting          | Value                             | Why                                              |
| :--------------- | :-------------------------------- | :----------------------------------------------- |
| `url`            | `jdbc:h2:mem:...;MODE=PostgreSQL` | Emulates PostgreSQL behavior                     |
| `ddl-auto`       | `create-drop`                     | Schema auto-created from entities, dropped after |
| `flyway.enabled` | `false`                           | Hibernate handles schema in tests                |
| `jwt.secret`     | test-only key                     | Fixed key for deterministic JWT tests            |

### 3. Test Utilities (`TestUtils.java`)

Reusable utility class providing:

| Method                  | Purpose                                     |
| :---------------------- | :------------------------------------------ |
| `setupDefaultContext()` | Sets both TenantContext + SecurityContext   |
| `clearContext()`        | Clears both (call in `@AfterEach`)          |
| `setTenant(UUID)`       | Sets TenantContext for test                 |
| `setSecurityUser(UUID)` | Sets SecurityContext with user as principal |

Fixed UUIDs for predictable test data:

- `TEST_TENANT_ID`: `00000000-0000-0000-0000-000000000001`
- `TEST_USER_ID`: `00000000-0000-0000-0000-000000000002`
- `TEST_ADMIN_ID`: `00000000-0000-0000-0000-000000000003`

### 4. Existing Test Dependencies

Already present in pom.xml (no changes needed):

- `spring-boot-starter-test` (includes JUnit 5, Mockito, AssertJ)
- `spring-security-test`

## Test Stack Summary

| Tool                     | Purpose                   |
| :----------------------- | :------------------------ |
| **JUnit 5**              | Test framework            |
| **Mockito**              | Mock dependencies         |
| **AssertJ**              | Fluent assertions         |
| **H2**                   | In-memory database        |
| **Spring Security Test** | Testing secured endpoints |

## Usage Pattern

```java
@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @BeforeEach
    void setup() {
        TestUtils.setupDefaultContext();
    }

    @AfterEach
    void tearDown() {
        TestUtils.clearContext();
    }

    @Test
    void shouldCalculateTotalsCorrectly() {
        // ... test code
    }
}
```

## Files Modified/Created

- `backend/pom.xml` — Added H2 dependency
- `backend/src/test/resources/application-test.yml` — Updated for H2
- `backend/src/test/java/com/lumora/pos/TestUtils.java` — New test utility class

## Also Fixed

- Removed unused `java.util.function.Function` import in `SaleService.java` (lint cleanup)

## Next Step

Phase 3, Step 7: Write core unit tests for `SaleService` (financial math protection).
