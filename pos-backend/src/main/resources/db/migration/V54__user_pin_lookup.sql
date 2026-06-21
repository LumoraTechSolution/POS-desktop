-- Blind index for staff PINs.
--
-- PINs are stored as salted bcrypt (`pin`), which is one-way and unequal for the
-- same plaintext, so two users sharing a PIN can't be found with SQL and can only
-- be detected by brute-forcing the 4-digit space per user — far too slow at the
-- bcrypt cost factor we use.
--
-- `pin_lookup` is a keyed, deterministic hash (HMAC-SHA256 of the PIN, keyed by
-- the server's JWT secret, hex-encoded) written whenever a PIN is set. Identical
-- PINs produce identical lookups, so collisions are a simple GROUP BY and
-- uniqueness is an indexed equality check. It is NOT the verifier — bcrypt `pin`
-- still authenticates; the keyed hash just keeps a 4-digit space from being
-- trivially reversible if the column leaks.
ALTER TABLE users ADD COLUMN pin_lookup VARCHAR(64);
CREATE INDEX idx_users_pin_lookup ON users(tenant_id, pin_lookup);
