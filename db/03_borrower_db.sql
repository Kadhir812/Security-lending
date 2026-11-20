-- Fresh schema; MySQL 8.0.16+; UTC. Not a migration.
-- No cross-service DB queries/FKs. External references are validated by backend.
DROP DATABASE IF EXISTS borrower_db;
CREATE DATABASE borrower_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE borrower_db;
SET time_zone = '+00:00';

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE roles (
  version BIGINT NOT NULL DEFAULT 0,
  role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255)
) ENGINE=InnoDB;

-- TECHNICAL
CREATE TABLE message_log (
  producer_service VARCHAR(80) NOT NULL,
  destination_service VARCHAR(80),
  message_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id CHAR(36) NOT NULL,
  service_name VARCHAR(80) NOT NULL,
  direction ENUM('INBOUND','OUTBOUND') NOT NULL,
  correlation_id VARCHAR(80) NOT NULL,
  causation_event_id CHAR(36),
  sender_organization_reference VARCHAR(80),
  receiver_organization_reference VARCHAR(80),
  application_message_type VARCHAR(100) NOT NULL,
  fix_message_type VARCHAR(8),
  fix_version VARCHAR(20),
  fix_session_id VARCHAR(150),
  fix_sequence_number BIGINT,
  raw_fix LONGTEXT,
  payload JSON NOT NULL,
  schema_version INT NOT NULL DEFAULT 1,
  kafka_topic VARCHAR(255),
  kafka_partition INT,
  kafka_offset BIGINT,
  processing_status ENUM('PENDING','PUBLISHED','PROCESSED','REJECTED','FAILED') NOT NULL DEFAULT 'PENDING',
  error_message TEXT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at DATETIME(6),
  UNIQUE (service_name, direction, event_id)
) ENGINE=InnoDB;

-- TECHNICAL
CREATE TABLE outbox_event (
  producer_service VARCHAR(80) NOT NULL,
  destination_service VARCHAR(80),
  outbox_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id CHAR(36) NOT NULL UNIQUE,
  service_name VARCHAR(80) NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_reference VARCHAR(80) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  correlation_id VARCHAR(80) NOT NULL,
  sender_organization_reference VARCHAR(80),
  receiver_organization_reference VARCHAR(80),
  event_type VARCHAR(100) NOT NULL,
  schema_version INT NOT NULL DEFAULT 1,
  kafka_topic VARCHAR(255) NOT NULL,
  partition_key VARCHAR(150) NOT NULL,
  payload JSON NOT NULL,
  status ENUM('PENDING','PUBLISHING','PUBLISHED','FAILED') NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  locked_by VARCHAR(150),
  locked_until DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  published_at DATETIME(6),
  last_error TEXT,
  CHECK (attempt_count >= 0 AND aggregate_version >= 0)
) ENGINE=InnoDB;

-- TECHNICAL
CREATE TABLE inbox_message (
  inbox_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  consumer_name VARCHAR(150) NOT NULL,
  event_id CHAR(36) NOT NULL,
  correlation_id VARCHAR(80) NOT NULL,
  aggregate_reference VARCHAR(80) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  payload JSON NOT NULL,
  kafka_topic VARCHAR(255) NOT NULL,
  kafka_partition INT NOT NULL,
  kafka_offset BIGINT NOT NULL,
  status ENUM('RECEIVED','PROCESSED','REJECTED') NOT NULL DEFAULT 'RECEIVED',
  received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at DATETIME(6),
  error_message TEXT,
  UNIQUE (consumer_name, event_id),
  CHECK (kafka_partition >= 0 AND kafka_offset >= 0)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE users (
  version BIGINT NOT NULL DEFAULT 0,
  identity_issuer VARCHAR(255) NOT NULL,
  UNIQUE (identity_issuer, identity_subject),
  user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_reference VARCHAR(80) NOT NULL,
  identity_subject VARCHAR(255) NOT NULL,
  username VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  phone VARCHAR(50),
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (organization_reference, username)
) ENGINE=InnoDB;

-- BORROWER-OWNED CASH BALANCE; starts at zero and changes only with a ledger transaction
CREATE TABLE borrower_cash_account (
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

-- Immutable cash movements and order holds; corrections are new reversing entries.
CREATE TABLE borrower_cash_transaction (
  version BIGINT NOT NULL DEFAULT 0,
  cash_transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  cash_account_id BIGINT NOT NULL,
  transaction_type ENUM('DEPOSIT','WITHDRAWAL','SHORT_SALE_PROCEEDS','BUY_TO_COVER','TRADE_BUY','TRADE_SELL','COLLATERAL_CASH_RESERVED','COLLATERAL_POSTED','COLLATERAL_RELEASED','FEE_CASH_RESERVED','LENDING_FEE_PAID','REBATE_RECEIVED','ORDER_CASH_RESERVED','ORDER_CASH_RELEASED','CASH_RESERVATION_RELEASED','REVERSAL') NOT NULL,
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

-- BORROWER HOLDING; SETTLED QUANTITY UPDATED FROM CUSTODIAN EVENTS
CREATE TABLE borrower_inventory (
  borrower_organization_reference VARCHAR(80) NOT NULL,
  source_contract_version BIGINT NOT NULL DEFAULT 0,
  inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  security_isin CHAR(12) NOT NULL,
  borrowed_quantity BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (borrowed_quantity >= 0)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE user_roles (
  version BIGINT NOT NULL DEFAULT 0,
  user_role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES users(user_id),
  FOREIGN KEY (role_id) REFERENCES roles(role_id)
) ENGINE=InnoDB;

-- SUBMITTED INTENT + COPIED DECISION
CREATE TABLE locate_request (
  borrower_organization_reference VARCHAR(80) NOT NULL,
  custodian_organization_reference VARCHAR(80) NOT NULL,
  submission_status ENUM('QUEUED','SENT','FAILED') NOT NULL DEFAULT 'QUEUED',
  decision_source_version BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  locate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  locate_reference VARCHAR(80) NOT NULL UNIQUE,
  msla_reference VARCHAR(80) NOT NULL,
  security_isin CHAR(12) NOT NULL,
  quantity BIGINT NOT NULL,
  approved_quantity BIGINT,
  created_by BIGINT NOT NULL,
  request_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expiry_time DATETIME(6),
  responded_at DATETIME(6),
  status ENUM('PENDING','APPROVED','REJECTED','USED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  response_reason VARCHAR(500),
  CHECK (quantity > 0),
  CHECK (approved_quantity IS NULL OR (approved_quantity > 0 AND approved_quantity <= quantity)),
  CHECK (expiry_time > request_time),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE borrower_trade_activity (
  version BIGINT NOT NULL DEFAULT 0,
  trade_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  trade_reference VARCHAR(80) NOT NULL UNIQUE,
  transaction_reference VARCHAR(80),
  borrower_organization_reference VARCHAR(80),
  security_isin CHAR(12),
  trade_type ENUM('BUY','SELL','SELL_SHORT','BUY_TO_COVER') NOT NULL,
  quantity BIGINT NOT NULL,
  price DECIMAL(20,6) NOT NULL,
  realized_profit_loss DECIMAL(20,2),
  trade_time DATETIME(6) NOT NULL,
  created_by BIGINT NOT NULL,
  CHECK (quantity > 0 AND price > 0),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;
CREATE INDEX idx_borrower_trade_positions
  ON borrower_trade_activity (borrower_organization_reference, security_isin, trade_id);

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE borrower_order (
order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_reference VARCHAR(80) NOT NULL UNIQUE,
  borrower_organization_reference VARCHAR(80) NOT NULL,
  security_isin CHAR(12) NOT NULL,
  transaction_reference VARCHAR(80),
  side ENUM('BUY','SELL') NOT NULL,
  order_quantity BIGINT NOT NULL,
  filled_quantity BIGINT NOT NULL DEFAULT 0,
  leaves_quantity BIGINT NOT NULL,
  average_fill_price DECIMAL(20,6),
  order_status ENUM('NEW','PARTIALLY_FILLED','FILLED','CANCELLED','REJECTED','EXPIRED') NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (order_quantity > 0 AND filled_quantity BETWEEN 0 AND order_quantity),
  CHECK (leaves_quantity >= 0 AND filled_quantity + leaves_quantity <= order_quantity),
  CHECK (order_status NOT IN ('FILLED','CANCELLED','REJECTED','EXPIRED') OR leaves_quantity = 0),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- SUBMITTED INTENT + COPIED DECISION
CREATE TABLE loan_request (
  borrower_organization_reference VARCHAR(80) NOT NULL,
  custodian_organization_reference VARCHAR(80) NOT NULL,
  submission_status ENUM('QUEUED','SENT','FAILED') NOT NULL DEFAULT 'QUEUED',
  decision_source_version BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_reference VARCHAR(80) NOT NULL UNIQUE,
  msla_reference VARCHAR(80) NOT NULL,
  locate_id BIGINT,
  security_isin CHAR(12) NOT NULL,
  quantity BIGINT NOT NULL,
  requested_settlement_date DATE NOT NULL,
  settlement_currency CHAR(3),
  created_by BIGINT NOT NULL,
  requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  status ENUM('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
  response_reason VARCHAR(500),
  responded_at DATETIME(6),
  CHECK (quantity > 0),
  INDEX idx_loan_request_locate_id (locate_id),
  FOREIGN KEY (locate_id) REFERENCES locate_request(locate_id),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- Borrower-owned read model populated only after a validated Custodian decision event.
CREATE TABLE loan_contract_view (
  version BIGINT NOT NULL DEFAULT 0,
  contract_view_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_reference VARCHAR(80) NOT NULL UNIQUE,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_snapshot JSON NOT NULL,
  received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (request_reference) REFERENCES loan_request(request_reference)
) ENGINE=InnoDB;

CREATE INDEX idx_outbox_event_processing ON outbox_event (status, next_attempt_at);
CREATE INDEX idx_inbox_message_processing ON inbox_message (correlation_id);
CREATE INDEX idx_message_log_processing ON message_log (correlation_id, created_at);
