-- Add local demo password login for Borrower and Custodian services.
-- Apply once to existing databases; never used by the Security & Market service.
SET time_zone = '+00:00';

USE borrower_db;
ALTER TABLE users
  ADD COLUMN organization_name VARCHAR(255) NULL,
  ADD COLUMN password_hash VARCHAR(100) NULL;

INSERT INTO roles (role_name, description) VALUES
  ('TRADER', 'Borrower application user')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

USE custodian_db;
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100) NULL;

INSERT INTO roles (role_name, description) VALUES
  ('CUSTODIAN_OPERATOR', 'Custodian application operator')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);
