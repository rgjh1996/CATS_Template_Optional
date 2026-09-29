-- Run in MySQL Workbench as a database administrator.
-- Replace the example password before executing. Keep it out of Git.
CREATE DATABASE IF NOT EXISTS cats_optional
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'cats'@'localhost'
  IDENTIFIED BY 'REPLACE_WITH_YOUR_LOCAL_PASSWORD';
GRANT ALL PRIVILEGES ON cats_optional.* TO 'cats'@'localhost';
-- If this user already exists, use its existing password or explicitly
-- ALTER USER 'cats'@'localhost' IDENTIFIED BY 'your-chosen-password';
-- The app creates its tables on startup. It does not create the database/user.
