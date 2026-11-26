-- Locate approvals remain valid until they are rejected or used by an approved loan.
USE borrower_db;
ALTER TABLE locate_request MODIFY COLUMN expiry_time DATETIME(6) NULL;

USE custodian_db;
ALTER TABLE locate_request MODIFY COLUMN expiry_time DATETIME(6) NULL;
