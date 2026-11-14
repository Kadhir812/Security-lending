-- Repairs the existing local databases to match the canonical service schemas.
-- MySQL 8.0.16+; UTC. Existing data is preserved.

USE custodian_db;

ALTER TABLE lender_inventory
  MODIFY COLUMN on_loan_quantity BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN reserved_quantity BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN available_quantity BIGINT
    GENERATED ALWAYS AS (total_quantity-on_loan_quantity-reserved_quantity) STORED;

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
