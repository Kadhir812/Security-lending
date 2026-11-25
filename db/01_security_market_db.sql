-- Fresh schema; MySQL 8.0.16+; UTC. Not a migration.
-- No cross-service DB queries/FKs. External references are validated by backend.
DROP DATABASE IF EXISTS security_market_db;
CREATE DATABASE security_market_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
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

-- Seed the demo market data so the application starts with known securities and prices.
INSERT INTO security_master (symbol, security_name, isin, security_type, currency, exchange_name, status)
VALUES
  ('RELIANCE', 'Reliance Industries Limited', 'INE002A01018', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('TCS', 'Tata Consultancy Services Limited', 'INE467B01029', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('HDFCBANK', 'HDFC Bank Limited', 'INE040A01034', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('INFY', 'Infosys Limited', 'INE009A01021', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('ICICIBANK', 'ICICI Bank Limited', 'INE090A01021', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('SBIN', 'State Bank of India', 'INE062A01020', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('LTIM', 'LTIMindtree Limited', 'INE214T01019', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('SUNPHARMA', 'Sun Pharmaceutical Industries Limited', 'INE044A01036', 'EQUITY', 'INR', 'NSE', 'ACTIVE');

INSERT INTO security_price (price_reference, security_id, market_price, price_timestamp, source_name)
SELECT UUID(), sm.security_id, 2584.500000, '2026-01-02 10:00:00.000000', 'DEMO_SIMULATED'
FROM security_master sm
WHERE sm.isin = 'INE002A01018'
UNION ALL
SELECT UUID(), sm.security_id, 3687.250000, '2026-01-02 10:00:00.000000', 'DEMO_SIMULATED'
FROM security_master sm
WHERE sm.isin = 'INE467B01029'
UNION ALL
SELECT UUID(), sm.security_id, 1722.750000, '2026-01-02 10:00:00.000000', 'DEMO_SIMULATED'
FROM security_master sm
WHERE sm.isin = 'INE040A01034'
UNION ALL
SELECT UUID(), sm.security_id, 1523.500000, '2026-01-02 10:00:00.000000', 'DEMO_SIMULATED'
FROM security_master sm
WHERE sm.isin = 'INE009A01021'
UNION ALL
SELECT UUID(), sm.security_id, 1178.000000, '2026-01-02 10:00:00.000000', 'DEMO_SIMULATED'
FROM security_master sm
WHERE sm.isin = 'INE090A01021'
UNION ALL
SELECT UUID(), sm.security_id, 890.250000, '2026-01-02 10:00:00.000000', 'DEMO_SIMULATED'
FROM security_master sm
WHERE sm.isin = 'INE062A01020'
UNION ALL
SELECT UUID(), sm.security_id, 642.125000, '2026-01-02 10:00:00.000000', 'DEMO_SIMULATED'
FROM security_master sm
WHERE sm.isin = 'INE214T01019'
UNION ALL
SELECT UUID(), sm.security_id, 1184.875000, '2026-01-02 10:00:00.000000', 'DEMO_SIMULATED'
FROM security_master sm
WHERE sm.isin = 'INE044A01036';
