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

CREATE INDEX idx_security_price_processing ON security_price (security_id, price_timestamp);
