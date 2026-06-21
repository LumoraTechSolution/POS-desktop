-- V12: Final fix to re-enable admin and ensure consistent status
UPDATE users 
SET is_active = TRUE 
WHERE email = 'admin@demo.lumora.com' OR email = 'admin@lumora.com';
