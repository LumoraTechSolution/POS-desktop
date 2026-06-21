# Step 16: Customer Management & Loyalty System

## Overview

Implement a comprehensive customer management system to track purchases, build a loyalty database, and associate sales with specific profiles.

## 📋 Task Breakdown

### 1. Database & Schema

- [ ] Create `V10__customer_schema.sql` migration.
- [ ] Implement `CustomerEntity` with tenant isolation.
- [ ] Link `SaleEntity` to `CustomerEntity`.

### 2. Backend API

- [ ] Develop `CustomerRepository`, `CustomerService`, and `CustomerController`.
- [ ] Implement paginated search (by name/phone).
- [ ] Add basic loyalty point increment logic on successful sales.

### 3. Frontend Dashboard

- [ ] Create Customer management UI in `/dashboard/customers`.
- [ ] Build Customer Create/Edit forms.

### 4. POS Integration

- [ ] Add customer selection search to the POS Cart section.
- [ ] Display customer summary (points/tier) when selected.
- [ ] Pass `customerId` in the `SaleRequest`.

## Status Tracking

- **Date**: 2026-02-21
- **Current Branch**: `feature/customer-management`
- **Current Task**: Initializing Schema
