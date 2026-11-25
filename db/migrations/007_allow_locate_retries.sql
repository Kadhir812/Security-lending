-- A Locate can be attached to another loan attempt if an earlier loan is rejected.
-- The approved Locate is still consumed atomically by the first approved loan.
USE borrower_db;
SET @add_locate_index = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'loan_request'
     AND INDEX_NAME = 'idx_loan_request_locate_id') = 0,
  'ALTER TABLE loan_request ADD INDEX idx_loan_request_locate_id (locate_id)',
  'SELECT 1');
PREPARE add_locate_index_stmt FROM @add_locate_index;
EXECUTE add_locate_index_stmt;
DEALLOCATE PREPARE add_locate_index_stmt;

SET @locate_unique_index = (
  SELECT MIN(INDEX_NAME)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'loan_request'
    AND COLUMN_NAME = 'locate_id'
    AND NON_UNIQUE = 0
);
SET @drop_locate_unique_index = IF(
  @locate_unique_index IS NULL,
  'SELECT 1',
  CONCAT('ALTER TABLE loan_request DROP INDEX `', REPLACE(@locate_unique_index, '`', '``'), '`'));
PREPARE drop_locate_unique_index_stmt FROM @drop_locate_unique_index;
EXECUTE drop_locate_unique_index_stmt;
DEALLOCATE PREPARE drop_locate_unique_index_stmt;

USE custodian_db;
SET @add_locate_index = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'loan_request'
     AND INDEX_NAME = 'idx_loan_request_locate_id') = 0,
  'ALTER TABLE loan_request ADD INDEX idx_loan_request_locate_id (locate_id)',
  'SELECT 1');
PREPARE add_locate_index_stmt FROM @add_locate_index;
EXECUTE add_locate_index_stmt;
DEALLOCATE PREPARE add_locate_index_stmt;

SET @locate_unique_index = (
  SELECT MIN(INDEX_NAME)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'loan_request'
    AND COLUMN_NAME = 'locate_id'
    AND NON_UNIQUE = 0
);
SET @drop_locate_unique_index = IF(
  @locate_unique_index IS NULL,
  'SELECT 1',
  CONCAT('ALTER TABLE loan_request DROP INDEX `', REPLACE(@locate_unique_index, '`', '``'), '`'));
PREPARE drop_locate_unique_index_stmt FROM @drop_locate_unique_index;
EXECUTE drop_locate_unique_index_stmt;
DEALLOCATE PREPARE drop_locate_unique_index_stmt;
