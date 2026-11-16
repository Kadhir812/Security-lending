-- Repairs the existing local databases to match the canonical service schemas.
-- MySQL 8.0.16+; UTC. Existing data is preserved.

USE custodian_db;

-- Remove stale backup tables introduced by a partial or outdated local schema.
DROP TABLE IF EXISTS lender_inventory_backup_20261110;
DROP TABLE IF EXISTS inbox_message_backup_20261110;
DROP TABLE IF EXISTS message_log_backup_20261110;
DROP TABLE IF EXISTS counterparty_credit_limit_backup_20261110;

ALTER TABLE lender_inventory
  MODIFY COLUMN on_loan_quantity BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN reserved_quantity BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN available_quantity BIGINT
    GENERATED ALWAYS AS (total_quantity-on_loan_quantity-reserved_quantity) STORED,
  MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Restore the canonical invariant that one lender cannot stock the same security twice.
SET @lender_inventory_unique_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'lender_inventory'
    AND index_name = 'uk_lender_inventory_org_isin'
);

SET @lender_inventory_sql = IF(
  @lender_inventory_unique_exists = 0,
  'ALTER TABLE lender_inventory ADD CONSTRAINT uk_lender_inventory_org_isin UNIQUE (organization_id, security_isin);',
  'SELECT 1'
);
PREPARE stmt FROM @lender_inventory_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Restore the canonical foreign key to the organization table.
SET @fk_lender_inventory_org_exists = (
  SELECT COUNT(*)
  FROM information_schema.referential_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'lender_inventory'
    AND constraint_name = 'fk_lender_inventory_organization'
);

SET @fk_lender_inventory_org_sql = IF(
  @fk_lender_inventory_org_exists = 0,
  'ALTER TABLE lender_inventory ADD CONSTRAINT fk_lender_inventory_organization FOREIGN KEY (organization_id) REFERENCES organization(organization_id);',
  'SELECT 1'
);
PREPARE stmt2 FROM @fk_lender_inventory_org_sql;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

ALTER TABLE counterparty_credit_limit
  MODIFY COLUMN used_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  MODIFY COLUMN reserved_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  MODIFY COLUMN available_amount DECIMAL(20,2)
    GENERATED ALWAYS AS (limit_amount-used_amount-reserved_amount) STORED;

ALTER TABLE message_log
  MODIFY COLUMN schema_version INT NOT NULL DEFAULT 1,
  MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE inbox_message
  MODIFY COLUMN received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

USE borrower_db;

ALTER TABLE message_log
  MODIFY COLUMN schema_version INT NOT NULL DEFAULT 1,
  MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE inbox_message
  MODIFY COLUMN received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
