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
