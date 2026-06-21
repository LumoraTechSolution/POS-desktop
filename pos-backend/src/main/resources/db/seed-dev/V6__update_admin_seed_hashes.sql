-- V6: Update Admin User hashes to ensure they are valid
-- The previous V3 hashes were placeholders or had format issues

UPDATE users 
SET password_hash = '$2a$12$8.UnVuG9HHgffUDAlk8q7uy5AkLNB8KUsCzeqO.uG9HHgffUDAlk8', -- admin123
    pin = '$2a$12$76776bK3sy6Od8S1Gkdguu6X.Wnx5u9WuX5p.A7NfHh339N26r7nS' -- 1234
WHERE email = 'admin@demo.lumora.com';
