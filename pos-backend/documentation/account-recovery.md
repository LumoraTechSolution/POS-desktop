# Account Recovery Runbook

This app uses **admin-mediated** password recovery — there is no email/SMTP, so
there are no "email me a reset link" flows. Recovery always goes through someone
with higher authority. Everyone who has a password reset for them is forced to
choose a new one on their next login (`users.must_change_password` /
`super_admins.password_change_required`).

## Tenant staff (cashier / manager / inventory manager)

- **Forgot password:** a tenant **ADMIN** opens *Employees → Reset PW* and sets a
  temporary password. The staff member signs in with it and is forced to change it.
  (`POST /api/v1/users/{id}/reset-password`)
- **Forgot email / which login they use:** the ADMIN looks it up in *Employees*
  (the email is shown on each row).
- **Forgot PIN:** the ADMIN sets a new PIN via *Employees → Edit*. The user can
  also change their own PIN from *My Profile* once signed in.

## Tenant ADMIN (the business owner account)

- **Forgot password / locked out:** the **Super Admin** opens
  *Super Admin → Tenants → \<tenant\> → Users*, finds the admin, and clicks
  *Reset*. The admin signs in with the temporary password and is forced to change it.
  (`POST /api/v1/super-admin/tenants/{id}/users/{userId}/reset-password`)
- **Forgot email:** the Super Admin reads it from the same Users tab.
- **First login after provisioning:** the password the Super Admin typed when
  creating the tenant is single-use — the admin must set their own on first login.

## Super Admin (platform operator)

There is intentionally no self-service or peer reset UI for super admins. Recovery
is a server-side bootstrap:

1. **Re-seed** the default account, or
2. **Update the row directly** in the `super_admins` table: set `password_hash` to
   a known BCrypt(12) hash and `password_change_required = true`, e.g.

   ```sql
   UPDATE super_admins
   SET password_hash = '<bcrypt-hash>',
       password_change_required = true,
       failed_login_attempts = 0,
       locked_until = NULL
   WHERE email = 'superadmin@lumora.com';
   ```

   On next login the forced-change flow makes the operator rotate it immediately.

> If multiple super admins are ever needed, build a proper super-admin management
> console instead of relying on this runbook.
