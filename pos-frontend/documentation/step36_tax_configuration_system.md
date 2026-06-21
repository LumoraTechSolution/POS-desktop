# Step 36: Tax Configuration System

## Overview

Implemented a professional, configurable tax system to replace the hardcoded 10% tax rate. Taxes are now category-aware and can be managed via a dedicated settings page. This ensures accurate financial calculations based on product types and tenant preferences.

## Implementation Details

### 1. Database & Schema

- **Migration (V15)**: Created the `tax_rates` table to store tax names, rates (decimal), and status (active/default).
- **Category Link**: Added an optional `tax_rate_id` to the `categories` table. Categories can now be assigned a specific tax rate which all products in that category will inherit.
- **Seeding**: Automatically seeded a 10% "Standard Tax" for all existing tenants to ensure zero breaking changes.

### 2. Backend Architecture

- **TaxRate Module**:
  - `TaxRateEntity` & `TaxRateRepository`: Core data layer.
  - `TaxRateService`: Handles CRUD and the core **Tax Resolution Chain**.
  - `TaxRateController`: REST endpoints for management.
- **Service Integration**:
  - `CategoryService`: Updated to handle tax rate assignment to categories.
  - `SaleService`: REPLACED hardcoded `0.10` with `taxRateService.getApplicableRate(product)`.

### 3. Frontend Architecture

- **Tax Settings UI**: Created a new page under `/settings` with:
  - KPI cards for tax statistics.
  - Registry table for all tax rates.
  - Modals for creating/editing rates with real-time validation.
- **POS Engine**:
  - Updated `useCart` hook to accept a `TaxContext`.
  - Implemented client-side tax resolution mirroring the backend logic.
  - Updated `terminal/page.tsx` to fetch active tax rates and categories on load.

## Key Files

- **Backend**:
  - `com.lumora.pos.tax.*` (New Package)
  - `SaleService.java` (Integration)
  - `CategoryEntity.java`, `CategoryService.java` (Mapping)
- **Frontend**:
  - `src/services/taxService.ts`
  - `src/app/(dashboard)/settings/page.tsx`
  - `src/hooks/useCart.ts`
  - `src/app/(pos)/terminal/page.tsx`

## Tax Resolution Order

1. **Product Category**: If the category has an active tax rate assigned.
2. **Tenant Default**: If no category tax, use the rate marked as `is_default` for the tenant.
3. **Tax-Exempt**: If neither is found, the rate is `0%`.
