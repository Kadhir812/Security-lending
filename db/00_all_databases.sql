-- Run this OR the four individual scripts, not both. Empty databases required.

-- Fresh schema; MySQL 8.0.16+; UTC. Not a migration.
-- No cross-service DB queries/FKs. External references are validated by backend.
CREATE DATABASE IF NOT EXISTS security_market_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE security_market_db;
SET time_zone = '+00:00';

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE security_master (
  version BIGINT NOT NULL DEFAULT 0,
  security_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol VARCHAR(50) NOT NULL,
  security_name VARCHAR(255) NOT NULL,
  isin CHAR(12) NOT NULL UNIQUE,
  security_type ENUM('EQUITY','CORPORATE_BOND','GOVT_BOND','ETF') NOT NULL,
  currency CHAR(3) NOT NULL,
  exchange_name VARCHAR(100),
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  INDEX idx_security_symbol (symbol)
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
CREATE TABLE security_price (
  version BIGINT NOT NULL DEFAULT 0,
  price_reference CHAR(36) NOT NULL UNIQUE,
  price_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  security_id BIGINT NOT NULL,
  market_price DECIMAL(20,6) NOT NULL,
  price_timestamp DATETIME(6) NOT NULL,
  source_name VARCHAR(100) NOT NULL,
  CHECK (market_price > 0),
  UNIQUE (security_id, price_timestamp, source_name),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id)
) ENGINE=InnoDB;

CREATE INDEX idx_outbox_event_processing ON outbox_event (status, next_attempt_at);
CREATE INDEX idx_inbox_message_processing ON inbox_message (correlation_id);
CREATE INDEX idx_message_log_processing ON message_log (correlation_id, created_at);
CREATE INDEX idx_security_price_processing ON security_price (security_id, price_timestamp);

-- Fresh schema; MySQL 8.0.16+; UTC. Not a migration.
-- No cross-service DB queries/FKs. External references are validated by backend.
CREATE DATABASE IF NOT EXISTS custodian_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE custodian_db;
SET time_zone = '+00:00';

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE organization (
  registered_by_custodian_id BIGINT NULL,
  created_by_user_id BIGINT NULL,
  updated_at DATETIME(6) NULL,
  updated_by_user_id BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  organization_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_reference VARCHAR(80) NOT NULL UNIQUE,
  organization_name VARCHAR(255) NOT NULL,
  organization_type ENUM('LENDER','BORROWER','CUSTODIAN') NOT NULL,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

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
  organization_id BIGINT NOT NULL,
  identity_subject VARCHAR(255) NOT NULL,
  username VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  phone VARCHAR(50),
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (organization_id, username),
  FOREIGN KEY (organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE lender_custodian_mandate (
  created_at DATETIME(6) NULL,
  created_by_user_id BIGINT NULL,
  updated_at DATETIME(6) NULL,
  updated_by_user_id BIGINT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  mandate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lender_org_id BIGINT NOT NULL,
  custodian_org_id BIGINT NOT NULL,
  mandate_reference VARCHAR(80) NOT NULL UNIQUE,
  effective_from DATE NOT NULL,
  effective_to DATE,
  status ENUM('ACTIVE','INACTIVE','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  CHECK (lender_org_id <> custodian_org_id),
  CHECK (effective_to IS NULL OR effective_to >= effective_from),
  FOREIGN KEY (lender_org_id) REFERENCES organization(organization_id),
  FOREIGN KEY (custodian_org_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE lender_inventory (
  inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  security_isin CHAR(12) NOT NULL,
  total_quantity BIGINT NOT NULL,
  on_loan_quantity BIGINT NOT NULL DEFAULT 0,
  reserved_quantity BIGINT NOT NULL DEFAULT 0,
  available_quantity BIGINT GENERATED ALWAYS AS (total_quantity-on_loan_quantity-reserved_quantity) STORED,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (organization_id, security_isin),
  CHECK (total_quantity >= 0 AND on_loan_quantity >= 0 AND reserved_quantity >= 0),
  CHECK (on_loan_quantity + reserved_quantity <= total_quantity),
  FOREIGN KEY (organization_id) REFERENCES organization(organization_id)
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

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE msla_agreement (
  created_by_user_id BIGINT NULL,
  updated_at DATETIME(6) NULL,
  updated_by_user_id BIGINT NULL,
  settlement_calendar_code VARCHAR(80) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  msla_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msla_reference VARCHAR(80) NOT NULL UNIQUE,
  mandate_id BIGINT NOT NULL,
  borrower_org_id BIGINT NOT NULL,
  effective_date DATE NOT NULL,
  expiry_date DATE,
  agreement_status ENUM('DRAFT','ACTIVE','INACTIVE','EXPIRED') NOT NULL DEFAULT 'DRAFT',
  base_currency CHAR(3) NOT NULL,
  eligible_securities JSON NOT NULL,
  collateral_type ENUM('CASH','ETF','GOVT_BOND','CORPORATE_BOND','EQUITY') NOT NULL DEFAULT 'CASH',
  collateral_coverage_pct DECIMAL(9,4) NOT NULL DEFAULT 105.0000,
  mtm_interval_minutes INT NOT NULL DEFAULT 60,
  margin_call_window_hours INT NOT NULL DEFAULT 24,
  recall_rule VARCHAR(500),
  default_rules JSON,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (expiry_date IS NULL OR expiry_date >= effective_date),
  CHECK (collateral_coverage_pct >= 100),
  CHECK (mtm_interval_minutes > 0 AND margin_call_window_hours > 0),
  FOREIGN KEY (mandate_id) REFERENCES lender_custodian_mandate(mandate_id),
  FOREIGN KEY (borrower_org_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE counterparty_credit_limit (
  credit_limit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msla_id BIGINT NOT NULL UNIQUE,
  limit_amount DECIMAL(20,2) NOT NULL,
  used_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  reserved_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  available_amount DECIMAL(20,2) GENERATED ALWAYS AS (limit_amount-used_amount-reserved_amount) STORED,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (limit_amount >= 0 AND used_amount >= 0 AND reserved_amount >= 0),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE collateral_haircut (
  version BIGINT NOT NULL DEFAULT 0,
  haircut_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msla_id BIGINT NOT NULL,
  collateral_type ENUM('CASH','GOVT_BOND','CORPORATE_BOND','EQUITY','ETF') NOT NULL,
  currency CHAR(3) NOT NULL,
  haircut_percentage DECIMAL(9,4) NOT NULL,
  effective_from DATETIME(6) NOT NULL,
  effective_to DATETIME(6),
  UNIQUE (msla_id, collateral_type, currency, effective_from),
  CHECK (haircut_percentage >= 0 AND haircut_percentage < 100),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE lending_fee (
  version BIGINT NOT NULL DEFAULT 0,
  fee_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msla_id BIGINT NOT NULL,
  security_isin CHAR(12),
  annual_fee_pct DECIMAL(10,6) NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  CHECK (annual_fee_pct >= 0),
  CHECK (effective_to IS NULL OR effective_to >= effective_from),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id)
) ENGINE=InnoDB;

-- RECEIVED INTENT + AUTHORITATIVE DECISION
CREATE TABLE locate_request (
  submitted_by_issuer VARCHAR(255) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  locate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  locate_reference VARCHAR(80) NOT NULL UNIQUE,
  msla_id BIGINT NOT NULL,
  security_isin CHAR(12) NOT NULL,
  quantity BIGINT NOT NULL,
  approved_quantity BIGINT,
  submitted_by_subject VARCHAR(255) NOT NULL,
  request_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expiry_time DATETIME(6) NOT NULL,
  responded_at DATETIME(6),
  status ENUM('PENDING','APPROVED','REJECTED','USED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  response_reason VARCHAR(500),
  CHECK (quantity > 0),
  CHECK (approved_quantity IS NULL OR (approved_quantity > 0 AND approved_quantity <= quantity)),
  CHECK (expiry_time > request_time),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE credit_limit_history (
  version BIGINT NOT NULL DEFAULT 0,
  history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  credit_limit_id BIGINT NOT NULL,
  old_limit DECIMAL(20,2) NOT NULL,
  new_limit DECIMAL(20,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  changed_by BIGINT NOT NULL,
  changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (old_limit >= 0 AND new_limit >= 0),
  FOREIGN KEY (credit_limit_id) REFERENCES counterparty_credit_limit(credit_limit_id),
  FOREIGN KEY (changed_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- RECEIVED INTENT + AUTHORITATIVE DECISION
CREATE TABLE loan_request (
  submitted_by_issuer VARCHAR(255) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_reference VARCHAR(80) NOT NULL UNIQUE,
  msla_id BIGINT NOT NULL,
  locate_id BIGINT UNIQUE,
  security_isin CHAR(12) NOT NULL,
  quantity BIGINT NOT NULL,
  requested_settlement_date DATE NOT NULL,
  settlement_currency CHAR(3),
  submitted_by_subject VARCHAR(255) NOT NULL,
  requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  status ENUM('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
  response_reason VARCHAR(500),
  responded_at DATETIME(6),
  CHECK (quantity > 0),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id),
  FOREIGN KEY (locate_id) REFERENCES locate_request(locate_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE loan_contract (
  accepted_valuation_snapshot JSON,
  settlement_calendar_version VARCHAR(80) NOT NULL,
  latest_valuation_result_reference CHAR(36),
  contract_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  loan_request_id BIGINT NOT NULL UNIQUE,
  quantity BIGINT NOT NULL,
  delivered_quantity BIGINT NOT NULL DEFAULT 0,
  returned_quantity BIGINT NOT NULL DEFAULT 0,
  outstanding_quantity BIGINT GENERATED ALWAYS AS (delivered_quantity-returned_quantity) STORED,
  settlement_currency CHAR(3) NOT NULL,
  agreed_annual_fee_pct DECIMAL(10,6) NOT NULL,
  agreed_annual_rebate_pct DECIMAL(10,6) NOT NULL DEFAULT 0,
  day_count_basis ENUM('ACT_365','ACT_360') NOT NULL DEFAULT 'ACT_365',
  collateral_coverage_pct DECIMAL(9,4) NOT NULL,
  collateral_terms JSON NOT NULL,
  latest_market_price DECIMAL(20,6),
  latest_loan_value DECIMAL(20,2),
  latest_required_collateral DECIMAL(20,2),
  latest_adjusted_collateral DECIMAL(20,2),
  latest_coverage_pct DECIMAL(12,6),
  latest_valuation_at DATETIME(6),
  reserved_credit_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  used_credit_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  trade_date DATE NOT NULL,
  settlement_date DATE NOT NULL,
  maturity_date DATE,
  recall_conditions VARCHAR(500),
  contract_status ENUM('APPROVED','PENDING_COLLATERAL','PENDING_SETTLEMENT','PARTIALLY_SETTLED','ON_LOAN','RECALLED','RETURNED','DEFAULTED','CANCELLED','SETTLEMENT_FAILED','CLOSED') NOT NULL DEFAULT 'APPROVED',
  version BIGINT NOT NULL DEFAULT 0,
  contract_created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (quantity > 0 AND delivered_quantity >= 0 AND delivered_quantity <= quantity),
  CHECK (returned_quantity >= 0 AND returned_quantity <= delivered_quantity),
  CHECK (agreed_annual_fee_pct >= 0 AND collateral_coverage_pct >= 100),
  CHECK (reserved_credit_amount >= 0 AND used_credit_amount >= 0),
  CHECK (settlement_date >= trade_date),
  CHECK (maturity_date IS NULL OR maturity_date >= settlement_date),
  FOREIGN KEY (loan_request_id) REFERENCES loan_request(request_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE credit_exposure_history (
  version BIGINT NOT NULL DEFAULT 0,
  exposure_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  credit_limit_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  valuation_result_reference CHAR(36) NOT NULL UNIQUE,
  idempotency_key VARCHAR(80) NOT NULL,
  input_hash CHAR(64) NOT NULL,
  old_used_amount DECIMAL(20,2) NOT NULL,
  new_used_amount DECIMAL(20,2) NOT NULL,
  old_available_amount DECIMAL(20,2) NOT NULL,
  new_available_amount DECIMAL(20,2) NOT NULL,
  breach_created BOOLEAN NOT NULL,
  reason VARCHAR(500) NOT NULL,
  changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (contract_id, idempotency_key),
  CHECK (old_used_amount >= 0 AND new_used_amount >= 0),
  FOREIGN KEY (credit_limit_id) REFERENCES counterparty_credit_limit(credit_limit_id),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE inventory_reservation (
  version BIGINT NOT NULL DEFAULT 0,
  reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL UNIQUE,
  inventory_id BIGINT NOT NULL,
  reserved_quantity BIGINT NOT NULL,
  consumed_quantity BIGINT NOT NULL DEFAULT 0,
  released_quantity BIGINT NOT NULL DEFAULT 0,
  remaining_quantity BIGINT GENERATED ALWAYS AS (reserved_quantity-consumed_quantity-released_quantity) STORED,
  status ENUM('ACTIVE','CONSUMED','RELEASED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at DATETIME(6) NOT NULL,
  CHECK (reserved_quantity > 0 AND consumed_quantity >= 0 AND released_quantity >= 0),
  CHECK (consumed_quantity + released_quantity <= reserved_quantity),
  CHECK (expires_at > created_at),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (inventory_id) REFERENCES lender_inventory(inventory_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE contract_status_history (
  version BIGINT NOT NULL DEFAULT 0,
  history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  old_status VARCHAR(40),
  new_status VARCHAR(40) NOT NULL,
  changed_by BIGINT,
  actor_type ENUM('USER','SYSTEM') NOT NULL,
  reason VARCHAR(500) NOT NULL,
  source_event_id CHAR(36),
  changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (actor_type <> 'USER' OR changed_by IS NOT NULL),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (changed_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE collateral (
  collateral_reference VARCHAR(80) NOT NULL UNIQUE,
  version BIGINT NOT NULL DEFAULT 0,
  collateral_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  collateral_type ENUM('CASH','GOVT_BOND','CORPORATE_BOND','EQUITY','ETF') NOT NULL,
  security_isin CHAR(12),
  currency CHAR(3) NOT NULL,
  pledged_quantity DECIMAL(24,6),
  released_quantity DECIMAL(24,6) NOT NULL DEFAULT 0,
  posted_cash_amount DECIMAL(20,2),
  released_cash_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  applied_haircut_pct DECIMAL(9,4) NOT NULL DEFAULT 0,
  posted_at DATETIME(6),
  status ENUM('PENDING','POSTED','PARTIALLY_RELEASED','RELEASED','FAILED') NOT NULL DEFAULT 'PENDING',
  CHECK (applied_haircut_pct >= 0 AND applied_haircut_pct < 100),
  CHECK ((collateral_type = 'CASH' AND security_isin IS NULL AND pledged_quantity IS NULL AND released_quantity = 0 AND posted_cash_amount IS NOT NULL AND posted_cash_amount > 0 AND released_cash_amount BETWEEN 0 AND posted_cash_amount)
   OR (collateral_type <> 'CASH' AND security_isin IS NOT NULL AND posted_cash_amount IS NULL AND released_cash_amount = 0 AND pledged_quantity IS NOT NULL AND pledged_quantity > 0 AND released_quantity BETWEEN 0 AND pledged_quantity)),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE recall_request (
  version BIGINT NOT NULL DEFAULT 0,
  recall_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recall_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_id BIGINT NOT NULL,
  requested_quantity BIGINT NOT NULL,
  returned_quantity BIGINT NOT NULL DEFAULT 0,
  requested_by BIGINT NOT NULL,
  requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  due_at DATETIME(6) NOT NULL,
  recall_reason VARCHAR(500) NOT NULL,
  recall_status ENUM('OPEN','PROCESSING','PARTIALLY_RETURNED','COMPLETED','OVERDUE','DEFAULTED','CANCELLED') NOT NULL DEFAULT 'OPEN',
  CHECK (requested_quantity > 0 AND returned_quantity BETWEEN 0 AND requested_quantity),
  CHECK (due_at > requested_at),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (requested_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE margin_call (
  version BIGINT NOT NULL DEFAULT 0,
  calculation_snapshot JSON NOT NULL,
  margin_call_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  margin_call_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_id BIGINT NOT NULL,
  valuation_result_reference CHAR(36) NOT NULL,
  required_collateral DECIMAL(20,2) NOT NULL,
  current_collateral DECIMAL(20,2) NOT NULL,
  shortfall_amount DECIMAL(20,2) GENERATED ALWAYS AS (GREATEST(required_collateral-current_collateral, 0)) STORED,
  status ENUM('OPEN','PARTIALLY_SATISFIED','SATISFIED','FAILED','BREACHED','CANCELLED') NOT NULL DEFAULT 'OPEN',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  due_at DATETIME(6) NOT NULL,
  resolved_at DATETIME(6),
  CHECK (required_collateral >= 0 AND current_collateral >= 0),
  CHECK (due_at > created_at),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE rebate_payment_history (
  version BIGINT NOT NULL DEFAULT 0,
  contract_id BIGINT NOT NULL,
  obligation_reference VARCHAR(80) NOT NULL,
  calculation_snapshot JSON NOT NULL,
  INDEX idx_payment_obligation (obligation_reference),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_reference VARCHAR(80) NOT NULL UNIQUE,
  calculation_result_reference CHAR(36) NOT NULL,
  from_organization_id BIGINT NOT NULL,
  to_organization_id BIGINT NOT NULL,
  payment_amount DECIMAL(20,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  status ENUM('PENDING','PAID','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  paid_at DATETIME(6),
  external_reference VARCHAR(150) UNIQUE,
  failure_reason VARCHAR(500),
  CHECK (payment_amount > 0 AND from_organization_id <> to_organization_id),
  CHECK (status <> 'PAID' OR paid_at IS NOT NULL),
  FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE lending_fee_payment (
  version BIGINT NOT NULL DEFAULT 0,
  contract_id BIGINT NOT NULL,
  obligation_reference VARCHAR(80) NOT NULL,
  calculation_snapshot JSON NOT NULL,
  INDEX idx_payment_obligation (obligation_reference),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_reference VARCHAR(80) NOT NULL UNIQUE,
  calculation_result_reference CHAR(36) NOT NULL,
  from_organization_id BIGINT NOT NULL,
  to_organization_id BIGINT NOT NULL,
  payment_amount DECIMAL(20,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  status ENUM('PENDING','PAID','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  paid_at DATETIME(6),
  external_reference VARCHAR(150) UNIQUE,
  failure_reason VARCHAR(500),
  CHECK (payment_amount > 0 AND from_organization_id <> to_organization_id),
  CHECK (status <> 'PAID' OR paid_at IS NOT NULL),
  FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE settlement_instruction (
  version BIGINT NOT NULL DEFAULT 0,
  instruction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  instruction_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_id BIGINT NOT NULL,
  collateral_id BIGINT,
  recall_id BIGINT,
  margin_call_id BIGINT,
  instruction_type ENUM('SECURITY_DELIVERY','COLLATERAL_TRANSFER','SECURITY_RETURN','COLLATERAL_RELEASE') NOT NULL,
  from_organization_id BIGINT NOT NULL,
  to_organization_id BIGINT NOT NULL,
  from_account VARCHAR(255) NOT NULL,
  to_account VARCHAR(255) NOT NULL,
  security_isin CHAR(12),
  quantity DECIMAL(24,6),
  amount DECIMAL(20,2),
  currency CHAR(3) NOT NULL,
  status ENUM('PENDING','PROCESSING','SETTLED','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  settlement_date DATE NOT NULL,
  settled_at DATETIME(6),
  external_confirmation_reference VARCHAR(150) UNIQUE,
  failure_reason VARCHAR(500),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (from_organization_id <> to_organization_id),
  CHECK ((quantity IS NOT NULL AND quantity > 0 AND security_isin IS NOT NULL AND amount IS NULL)
   OR (amount IS NOT NULL AND amount > 0 AND quantity IS NULL AND security_isin IS NULL)),
  CHECK ((instruction_type IN ('SECURITY_DELIVERY','SECURITY_RETURN') AND collateral_id IS NULL AND quantity IS NOT NULL)
   OR (instruction_type IN ('COLLATERAL_TRANSFER','COLLATERAL_RELEASE') AND collateral_id IS NOT NULL)),
  CHECK (status <> 'SETTLED' OR settled_at IS NOT NULL),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (collateral_id) REFERENCES collateral(collateral_id),
  FOREIGN KEY (recall_id) REFERENCES recall_request(recall_id),
  FOREIGN KEY (margin_call_id) REFERENCES margin_call(margin_call_id),
  FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE inventory_movement (
  version BIGINT NOT NULL DEFAULT 0,
  movement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  settlement_instruction_id BIGINT NOT NULL UNIQUE,
  security_isin CHAR(12) NOT NULL,
  from_organization_id BIGINT NOT NULL,
  to_organization_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL,
  movement_type ENUM('LOAN_DELIVERY','RETURN','RECALL_RETURN') NOT NULL,
  movement_timestamp DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (quantity > 0 AND from_organization_id <> to_organization_id),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (settlement_instruction_id) REFERENCES settlement_instruction(instruction_id),
  FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE margin_call_transaction_log (
  version BIGINT NOT NULL DEFAULT 0,
  log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  margin_call_id BIGINT NOT NULL,
  settlement_instruction_id BIGINT,
  activity_type ENUM('ISSUED','TOP_UP_CONFIRMED','SATISFIED','BREACHED','FAILED','CANCELLED','NOTE') NOT NULL,
  amount DECIMAL(20,2),
  source_event_id CHAR(36),
  notes VARCHAR(500),
  activity_timestamp DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (amount IS NULL OR amount >= 0),
  UNIQUE (margin_call_id, settlement_instruction_id, activity_type),
  FOREIGN KEY (margin_call_id) REFERENCES margin_call(margin_call_id),
  FOREIGN KEY (settlement_instruction_id) REFERENCES settlement_instruction(instruction_id)
) ENGINE=InnoDB;

CREATE INDEX idx_outbox_event_processing ON outbox_event (status, next_attempt_at);
CREATE INDEX idx_inbox_message_processing ON inbox_message (correlation_id);
CREATE INDEX idx_message_log_processing ON message_log (correlation_id, created_at);
CREATE INDEX idx_margin_call_processing ON margin_call (status, due_at);
CREATE INDEX idx_recall_request_processing ON recall_request (recall_status, due_at);
CREATE INDEX idx_settlement_instruction_processing ON settlement_instruction (status, settlement_date);
CREATE INDEX idx_inventory_reservation_processing ON inventory_reservation (status, expires_at);
CREATE INDEX idx_loan_contract_processing ON loan_contract (contract_status);

-- Fresh schema; MySQL 8.0.16+; UTC. Not a migration.
-- No cross-service DB queries/FKs. External references are validated by backend.
CREATE DATABASE IF NOT EXISTS borrower_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
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
  expiry_time DATETIME(6) NOT NULL,
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
  transaction_reference VARCHAR(80) NOT NULL,
  trade_type ENUM('SELL_SHORT','BUY_TO_COVER') NOT NULL,
  quantity BIGINT NOT NULL,
  price DECIMAL(20,6) NOT NULL,
  realized_profit_loss DECIMAL(20,2),
  trade_time DATETIME(6) NOT NULL,
  created_by BIGINT NOT NULL,
  CHECK (quantity > 0 AND price > 0),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

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
  locate_id BIGINT UNIQUE,
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

-- Fresh schema; MySQL 8.0.16+; UTC. Not a migration.
-- No cross-service DB queries/FKs. External references are validated by backend.
CREATE DATABASE IF NOT EXISTS valuation_accrual_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE valuation_accrual_db;
SET time_zone = '+00:00';

-- AUTHORITATIVE LOCAL RECORD
CREATE TABLE calculation_run (
  version BIGINT NOT NULL DEFAULT 0,
  input_schema_version INT NOT NULL DEFAULT 1,
  inputs JSON NOT NULL,
  price_snapshots JSON NOT NULL,
  rule_parameters JSON NOT NULL,
  implementation_digest VARCHAR(128) NOT NULL,
run_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_reference CHAR(36) NOT NULL UNIQUE,
  requester_service VARCHAR(80) NOT NULL,
  request_reference VARCHAR(80) NOT NULL,
  request_payload_hash CHAR(64) NOT NULL,
  correlation_reference VARCHAR(80) NOT NULL,
  calculation_type ENUM('PREVIEW','APPROVAL','MTM','MARGIN','RELEASE','LENDING_FEE','REBATE','PAYMENT_DUE','RETURN_EXPOSURE') NOT NULL,
  purpose ENUM('PREVIEW','AUTHORITATIVE') NOT NULL,
  transaction_reference VARCHAR(80),
  borrower_organization_reference VARCHAR(80) NOT NULL,
  input_contract_version BIGINT,
  valuation_at DATETIME(6) NOT NULL,
  period_start DATE,
  period_end DATE,
  currency CHAR(3) NOT NULL,
  rule_version VARCHAR(80) NOT NULL,
  supersedes_run_reference CHAR(36),
  status ENUM('QUEUED','RUNNING','COMPLETED','FAILED') NOT NULL DEFAULT 'QUEUED',
  attempt_count INT NOT NULL DEFAULT 0,
  locked_until DATETIME(6),
  error_message TEXT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at DATETIME(6),
  UNIQUE (requester_service, request_reference),
  CHECK (period_end IS NULL OR period_start IS NULL OR period_end >= period_start),
  CHECK (purpose <> 'AUTHORITATIVE' OR requester_service = 'CUSTODIAN')
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
CREATE TABLE calculation_result (
  result_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  result_reference CHAR(36) NOT NULL UNIQUE,
  run_id BIGINT NOT NULL,
  item_key VARCHAR(150) NOT NULL,
  result_type ENUM('PREVIEW','APPROVAL','MTM','COLLATERAL_VALUATION','MARGIN','RELEASE','LENDING_FEE','REBATE','PAYMENT_DUE','RETURN_EXPOSURE') NOT NULL,
  transaction_reference VARCHAR(80),
  collateral_reference VARCHAR(80),
  calculation_date DATE,
  currency CHAR(3) NOT NULL,
  amount DECIMAL(20,6),
  loan_value DECIMAL(20,2),
  required_collateral DECIMAL(20,2),
  adjusted_collateral DECIMAL(20,2),
  coverage_pct DECIMAL(12,6),
  result_schema_version INT NOT NULL DEFAULT 1,
  details JSON NOT NULL,
  supersedes_result_reference CHAR(36),
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (run_id, item_key),
  CHECK (loan_value IS NULL OR loan_value >= 0),
  CHECK (required_collateral IS NULL OR required_collateral >= 0),
  CHECK (adjusted_collateral IS NULL OR adjusted_collateral >= 0),
  FOREIGN KEY (run_id) REFERENCES calculation_run(run_id)
) ENGINE=InnoDB;

CREATE INDEX idx_outbox_event_processing ON outbox_event (status, next_attempt_at);
CREATE INDEX idx_inbox_message_processing ON inbox_message (correlation_id);
CREATE INDEX idx_message_log_processing ON message_log (correlation_id, created_at);
CREATE INDEX idx_calculation_run_processing ON calculation_run (status, created_at);
CREATE INDEX idx_calculation_result_processing ON calculation_result (transaction_reference, calculation_date, result_type);
