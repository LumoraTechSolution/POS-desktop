-- Force tenant users to rotate an admin-set password on their next login.
-- Mirrors super_admins.password_change_required (V40). Set TRUE when:
--   * a tenant is provisioned (the super admin's chosen admin password is single-use), or
--   * a tenant ADMIN / super admin resets a user's password.
-- Existing users keep their password (default FALSE) so the change is non-disruptive.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
