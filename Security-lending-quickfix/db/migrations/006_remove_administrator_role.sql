-- Remove obsolete administrator role assignments and seeds from existing databases.
-- Existing users assigned only this role will no longer be able to access APIs.
SET time_zone = '+00:00';

USE borrower_db;
DELETE user_role
FROM user_roles user_role
JOIN roles role_record ON role_record.role_id = user_role.role_id
WHERE role_record.role_name = 'ADMINISTRATOR';
DELETE FROM roles WHERE role_name = 'ADMINISTRATOR';

USE custodian_db;
DELETE user_role
FROM user_roles user_role
JOIN roles role_record ON role_record.role_id = user_role.role_id
WHERE role_record.role_name = 'ADMINISTRATOR';
DELETE FROM roles WHERE role_name = 'ADMINISTRATOR';
