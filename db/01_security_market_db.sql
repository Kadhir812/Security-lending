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
