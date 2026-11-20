-- Reusable seed data for the market database.
-- Run this after 01_security_market_db.sql, then run 07_security_market_trade_seed.sql.

USE security_market_db;
SET time_zone = '+00:00';

INSERT INTO security_master (symbol, security_name, isin, security_type, currency, exchange_name, status)
VALUES
  ('RELIANCE', 'Reliance Industries Limited', 'INE002A01018', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('TCS', 'Tata Consultancy Services Limited', 'INE467B01029', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('HDFCBANK', 'HDFC Bank Limited', 'INE040A01034', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('INFY', 'Infosys Limited', 'INE009A01021', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('ICICIBANK', 'ICICI Bank Limited', 'INE090A01021', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('SBIN', 'State Bank of India', 'INE062A01020', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('LTIM', 'LTIMindtree Limited', 'INE214T01019', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('SUNPHARMA', 'Sun Pharmaceutical Industries Limited', 'INE044A01036', 'EQUITY', 'INR', 'NSE', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  symbol = VALUES(symbol),
  security_name = VALUES(security_name),
  security_type = VALUES(security_type),
  currency = VALUES(currency),
  exchange_name = VALUES(exchange_name),
  status = VALUES(status);

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
