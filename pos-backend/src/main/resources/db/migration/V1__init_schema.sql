-- =====================================================
-- V1: Initial Schema — Tenants, Users, Roles, Permissions
-- Lumora POS System
-- =====================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- TENANTS — Multi-tenant root table
-- =====================================================
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    domain      VARCHAR(255) UNIQUE,
    settings    JSONB DEFAULT '{}',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tenants_domain ON tenants(domain);

-- =====================================================
-- PERMISSIONS — Granular permission definitions
-- =====================================================
CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,       -- e.g., 'SALES_CREATE', 'INVENTORY_EDIT'
    module      VARCHAR(50)  NOT NULL,       -- e.g., 'SALES', 'INVENTORY', 'REPORTS'
    description VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);

CREATE INDEX idx_permissions_tenant ON permissions(tenant_id);
CREATE INDEX idx_permissions_module ON permissions(tenant_id, module);

-- =====================================================
-- ROLES — RBAC roles scoped per tenant
-- =====================================================
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,       -- e.g., 'ADMIN', 'MANAGER', 'CASHIER'
    description VARCHAR(255),
    is_system   BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE = cannot be deleted
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(tenant_id, name)
);

CREATE INDEX idx_roles_tenant ON roles(tenant_id);

-- =====================================================
-- ROLE_PERMISSIONS — Many-to-many
-- =====================================================
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- =====================================================
-- USERS — System users scoped per tenant
-- =====================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    pin             VARCHAR(255),            -- Hashed PIN for quick cashier login
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    UNIQUE(tenant_id, email)
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(tenant_id, email);

-- =====================================================
-- USER_ROLES — Many-to-many
-- =====================================================
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- =====================================================
-- AUDIT_LOG — Track all critical actions
-- =====================================================
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id),
    action      VARCHAR(50) NOT NULL,        -- e.g., 'CREATE', 'UPDATE', 'DELETE', 'LOGIN'
    entity_type VARCHAR(100) NOT NULL,       -- e.g., 'USER', 'PRODUCT', 'TRANSACTION'
    entity_id   UUID,
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(500),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant ON audit_log(tenant_id);
CREATE INDEX idx_audit_entity ON audit_log(tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_log(tenant_id, user_id);
CREATE INDEX idx_audit_created ON audit_log(tenant_id, created_at);

-- =====================================================
-- SEED: Default tenant and system permissions
-- =====================================================
INSERT INTO tenants (id, name, domain) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Demo Business', 'demo.lumora.com');

-- System permissions by module
INSERT INTO permissions (tenant_id, name, module, description) VALUES
    -- Sales
    ('a0000000-0000-0000-0000-000000000001', 'SALES_CREATE',     'SALES',     'Create new sales transactions'),
    ('a0000000-0000-0000-0000-000000000001', 'SALES_VOID',       'SALES',     'Void existing transactions'),
    ('a0000000-0000-0000-0000-000000000001', 'SALES_DISCOUNT',   'SALES',     'Apply discounts to transactions'),
    ('a0000000-0000-0000-0000-000000000001', 'SALES_REFUND',     'SALES',     'Process refunds'),
    -- Inventory
    ('a0000000-0000-0000-0000-000000000001', 'INVENTORY_VIEW',   'INVENTORY', 'View inventory'),
    ('a0000000-0000-0000-0000-000000000001', 'INVENTORY_EDIT',   'INVENTORY', 'Edit stock levels'),
    ('a0000000-0000-0000-0000-000000000001', 'INVENTORY_CREATE', 'INVENTORY', 'Create products'),
    ('a0000000-0000-0000-0000-000000000001', 'INVENTORY_DELETE', 'INVENTORY', 'Archive products'),
    -- Customers
    ('a0000000-0000-0000-0000-000000000001', 'CUSTOMER_VIEW',    'CUSTOMERS', 'View customer data'),
    ('a0000000-0000-0000-0000-000000000001', 'CUSTOMER_EDIT',    'CUSTOMERS', 'Edit customer data'),
    -- Employees
    ('a0000000-0000-0000-0000-000000000001', 'EMPLOYEE_VIEW',    'EMPLOYEES', 'View employee data'),
    ('a0000000-0000-0000-0000-000000000001', 'EMPLOYEE_EDIT',    'EMPLOYEES', 'Edit employee data'),
    -- Reports
    ('a0000000-0000-0000-0000-000000000001', 'REPORTS_VIEW',     'REPORTS',   'View reports'),
    ('a0000000-0000-0000-0000-000000000001', 'REPORTS_EXPORT',   'REPORTS',   'Export reports'),
    -- Settings
    ('a0000000-0000-0000-0000-000000000001', 'SETTINGS_VIEW',    'SETTINGS',  'View system settings'),
    ('a0000000-0000-0000-0000-000000000001', 'SETTINGS_EDIT',    'SETTINGS',  'Edit system settings'),
    ('a0000000-0000-0000-0000-000000000001', 'USERS_MANAGE',     'SETTINGS',  'Manage users and roles');

-- System roles
INSERT INTO roles (id, tenant_id, name, description, is_system) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'ADMIN',             'Full system access',                TRUE),
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', 'MANAGER',           'Store manager access',              TRUE),
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', 'CASHIER',           'Cashier/sales associate access',    TRUE),
    ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000001', 'INVENTORY_MANAGER', 'Inventory management access',       TRUE);

-- Admin gets ALL permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-0000-0000-000000000001', id FROM permissions
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001';

-- Manager gets sales, inventory, customer, employee, reports
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-0000-0000-000000000002', id FROM permissions
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND module IN ('SALES', 'INVENTORY', 'CUSTOMERS', 'EMPLOYEES', 'REPORTS');

-- Cashier gets sales create, customer view
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-0000-0000-000000000003', id FROM permissions
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND name IN ('SALES_CREATE', 'CUSTOMER_VIEW');

-- Inventory Manager gets inventory + reports view
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'b0000000-0000-0000-0000-000000000004', id FROM permissions
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND (module = 'INVENTORY' OR name = 'REPORTS_VIEW');
