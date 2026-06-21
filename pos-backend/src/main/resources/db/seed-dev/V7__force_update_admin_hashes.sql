-- V7: Force update Admin User hashes
UPDATE users SET password_hash = '$2b$12$xUe6FODex27JHNp/sD3IDuvSvePXgS35IDKibfd9fskpUjund.pwu', pin = '$2b$12$KuH.IjrC//d6QXmc9NbAPea.Fy.1sWpSpM5jN9a48TQm3Q9C/6Zg6' WHERE email = 'admin@demo.lumora.com';
