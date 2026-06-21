# Step 6: Fix Flyway Migration Checksum Mismatch

## Issue Summary

The backend failed to start due to a `FlywayValidateException`. Specifically, a checksum mismatch was detected for migration version `V7__force_update_admin_hashes.sql`.

- **Applied to database**: 545046864
- **Resolved locally**: 682351581

This occurs when a migration file is modified after it has already been executed on the target database environment.

## Root Cause

The file `V7__force_update_admin_hashes.sql` was likely modified to update seeds or fix hashes without incrementing the version number, leading to a conflict with the recorded state in `flyway_schema_history`.

## Resolution Strategy

1. **Identify Change**: Verified that `V7` updates the admin user password and PIN hashes.
2. **Repair Schema History**: Use `flyway:repair` to update the checksums in the database to match the current local files. This is safe in development environments where migrations are being iteratively refined.
3. **Verify Startup**: Restart the backend to ensure validation passes and the service is healthy.

## Implementation Steps

- [x] Analyze Flyway logs and migration files.
- [x] Temporarily disable Flyway validation in `application-dev.yml`.
- [x] Manually repair the checksum in PostgreSQL using `docker exec`.
- [x] Re-enable Flyway validation and restart the Spring Boot application.
- [x] Verify successful connection and application health.
