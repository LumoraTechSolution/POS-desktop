-- =====================================================
-- V50: Desktop License Keys (product activation)
-- One key is sold per desktop install. On first launch the
-- desktop app redeems the key against /api/v1/activation/activate,
-- which node-locks it to that machine's hardware fingerprint and
-- returns an Ed25519-signed license token the app verifies offline
-- on every launch. See docs/desktop-product-activation.md.
-- Only the SHA-256 hash of the key is stored, never the plaintext.
-- =====================================================
CREATE TABLE license_keys (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key_hash           VARCHAR(64)  NOT NULL UNIQUE,            -- SHA-256 hex of the normalized key
    key_prefix         VARCHAR(16)  NOT NULL,                   -- e.g. "LUM-4F8K2" for admin display
    customer_name      VARCHAR(255) NOT NULL,
    customer_email     VARCHAR(255),
    edition            VARCHAR(32)  NOT NULL DEFAULT 'STANDARD',
    features           VARCHAR(512) NOT NULL DEFAULT '',        -- comma-separated feature codes
    max_activations    INT          NOT NULL DEFAULT 1,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ISSUED',  -- ISSUED | ACTIVE | REVOKED | EXPIRED
    bound_fingerprint  VARCHAR(128),                            -- SHA-256 hex of the machine fingerprint
    bound_machine_name VARCHAR(255),
    activated_at       TIMESTAMP,
    expires_at         TIMESTAMP,                               -- NULL = perpetual
    notes              TEXT,
    created_by         VARCHAR(64),                             -- super admin id that issued the key
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_license_keys_hash        ON license_keys(key_hash);
CREATE INDEX idx_license_keys_status      ON license_keys(status);
CREATE INDEX idx_license_keys_fingerprint ON license_keys(bound_fingerprint);
