-- Add borrower cash balance and posted cash movement records.
USE borrower_db;

CREATE TABLE IF NOT EXISTS borrower_cash_account (
  version BIGINT NOT NULL DEFAULT 0,
  account_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  borrower_organization_reference VARCHAR(80) NOT NULL,
  currency CHAR(3) NOT NULL,
  cash_balance DECIMAL(20,2) NOT NULL DEFAULT 0,
  status ENUM('ACTIVE','SUSPENDED','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE (borrower_organization_reference, currency),
  CHECK (cash_balance >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS borrower_cash_transaction (
  cash_transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  recorded_by_user_id BIGINT,
  transaction_reference VARCHAR(150) NOT NULL UNIQUE,
  transaction_type ENUM('CREDIT','DEBIT') NOT NULL,
  activity_type VARCHAR(80) NOT NULL,
  amount DECIMAL(20,2) NOT NULL,
  related_reference_type VARCHAR(40),
  related_reference VARCHAR(80),
  description VARCHAR(255) NOT NULL,
  transaction_time DATETIME(6) NOT NULL,
  recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_borrower_cash_transaction_account_time (account_id, transaction_time),
  INDEX idx_borrower_cash_transaction_related_reference (related_reference_type, related_reference),
  CHECK (amount > 0),
  CHECK ((related_reference_type IS NULL AND related_reference IS NULL)
    OR (related_reference_type IS NOT NULL AND related_reference IS NOT NULL)),
  FOREIGN KEY (account_id) REFERENCES borrower_cash_account(account_id),
  FOREIGN KEY (recorded_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
