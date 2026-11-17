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
  sedol VARCHAR(7),
  sector VARCHAR(100),
  lot_size BIGINT NOT NULL DEFAULT 1,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  INDEX idx_security_symbol (symbol),
  CHECK (lot_size > 0)
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

INSERT INTO security_master (
  version, symbol, security_name, isin, security_type, currency, exchange_name,
  status, sedol, sector, lot_size
) VALUES
  (0, 'INFY', 'Infosys Limited', 'INE009A01021', 'EQUITY', 'INR', 'NSE',
   'ACTIVE', '6205122', 'IT Services', 1),
  (0, 'TCS', 'Tata Consultancy Services Ltd.', 'INE467B01029', 'EQUITY', 'INR', 'NSE',
   'ACTIVE', 'B01NPJ1', 'IT Services', 1);
