-- Cash account and immutable transaction ledgers for borrower and lender balances.
-- Existing business data is preserved. New accounts start at zero.
USE borrower_db;

CREATE TABLE IF NOT EXISTS borrower_cash_account (
  version BIGINT NOT NULL DEFAULT 0,
  cash_account_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_reference VARCHAR(80) NOT NULL UNIQUE,
  borrower_organization_reference VARCHAR(80) NOT NULL,
  currency CHAR(3) NOT NULL,
  cash_balance DECIMAL(20,2) NOT NULL DEFAULT 0,
  reserved_balance DECIMAL(20,2) NOT NULL DEFAULT 0,
  available_balance DECIMAL(20,2) GENERATED ALWAYS AS (cash_balance-reserved_balance) STORED,
  status ENUM('ACTIVE','FROZEN','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (borrower_organization_reference, currency),
  CHECK (cash_balance >= 0 AND reserved_balance >= 0 AND reserved_balance <= cash_balance)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS borrower_cash_transaction (
  version BIGINT NOT NULL DEFAULT 0,
  cash_transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  cash_account_id BIGINT NOT NULL,
  transaction_type ENUM('DEPOSIT','WITHDRAWAL','SHORT_SALE_PROCEEDS','BUY_TO_COVER','TRADE_BUY','TRADE_SELL','COLLATERAL_POSTED','COLLATERAL_RELEASED','LENDING_FEE_PAID','REBATE_RECEIVED','ORDER_CASH_RESERVED','ORDER_CASH_RELEASED','REVERSAL') NOT NULL,
  source_type ENUM('MANUAL','TRADE','ORDER','LOAN_CONTRACT','SETTLEMENT','FEE_PAYMENT','REBATE','REVERSAL') NOT NULL,
  source_reference VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  cash_delta DECIMAL(20,2) NOT NULL DEFAULT 0,
  reserved_delta DECIMAL(20,2) NOT NULL DEFAULT 0,
  cash_balance_after DECIMAL(20,2) NOT NULL,
  reserved_balance_after DECIMAL(20,2) NOT NULL,
  description VARCHAR(500),
  created_by_user_id BIGINT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (cash_account_id, idempotency_key),
  INDEX idx_borrower_cash_transaction_source (source_type, source_reference),
  INDEX idx_borrower_cash_transaction_account_time (cash_account_id, created_at),
  CHECK ((cash_delta <> 0 OR reserved_delta <> 0) AND cash_balance_after >= 0 AND reserved_balance_after >= 0 AND reserved_balance_after <= cash_balance_after),
  FOREIGN KEY (cash_account_id) REFERENCES borrower_cash_account(cash_account_id),
  FOREIGN KEY (created_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

USE custodian_db;

CREATE TABLE IF NOT EXISTS lender_cash_account (
  version BIGINT NOT NULL DEFAULT 0,
  cash_account_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_reference VARCHAR(80) NOT NULL UNIQUE,
  organization_id BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  cash_balance DECIMAL(20,2) NOT NULL DEFAULT 0,
  restricted_balance DECIMAL(20,2) NOT NULL DEFAULT 0,
  available_balance DECIMAL(20,2) GENERATED ALWAYS AS (cash_balance-restricted_balance) STORED,
  status ENUM('ACTIVE','FROZEN','CLOSED') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (organization_id, currency),
  CHECK (cash_balance >= 0 AND restricted_balance >= 0 AND restricted_balance <= cash_balance),
  FOREIGN KEY (organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lender_cash_transaction (
  version BIGINT NOT NULL DEFAULT 0,
  cash_transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  cash_account_id BIGINT NOT NULL,
  transaction_type ENUM('DEPOSIT','WITHDRAWAL','COLLATERAL_RECEIVED','COLLATERAL_RELEASED','LENDING_FEE_RECEIVED','REBATE_PAID','REVERSAL') NOT NULL,
  source_type ENUM('MANUAL','LOAN_CONTRACT','SETTLEMENT','FEE_PAYMENT','REBATE','REVERSAL') NOT NULL,
  source_reference VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  cash_delta DECIMAL(20,2) NOT NULL DEFAULT 0,
  restricted_delta DECIMAL(20,2) NOT NULL DEFAULT 0,
  cash_balance_after DECIMAL(20,2) NOT NULL,
  restricted_balance_after DECIMAL(20,2) NOT NULL,
  description VARCHAR(500),
  created_by_user_id BIGINT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (cash_account_id, idempotency_key),
  INDEX idx_lender_cash_transaction_source (source_type, source_reference),
  INDEX idx_lender_cash_transaction_account_time (cash_account_id, created_at),
  CHECK ((cash_delta <> 0 OR restricted_delta <> 0) AND cash_balance_after >= 0 AND restricted_balance_after >= 0 AND restricted_balance_after <= cash_balance_after),
  FOREIGN KEY (cash_account_id) REFERENCES lender_cash_account(cash_account_id),
  FOREIGN KEY (created_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
