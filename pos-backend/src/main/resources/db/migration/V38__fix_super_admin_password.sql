-- =====================================================
-- V38: Fix Super Admin password hash
-- The hash seeded in V24/V25 was malformed (cost prefix
-- edited from $2a$10$ to $2a$12$ without recomputing the
-- digest), so no password verified against it.
-- This sets a valid BCrypt hash for the documented
-- default password "SuperAdmin@2024".
-- =====================================================

UPDATE super_admins
SET password_hash = '$2b$12$bBTipiHJmUFU/FH/oA3tJOvrBuohfbfNJw.bMQVEbN.fFDL243Xcm'
WHERE email = 'superadmin@lumora.com';
