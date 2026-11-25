-- Demo-only equities and a deterministic six-month weekday price history.
-- Prices are simulated and must not be presented as live exchange data.
-- MySQL 8.0.16+; run after 01_security_market_db.sql, or after 00_all_databases.sql.
USE security_market_db;
SET time_zone = '+00:00';

INSERT INTO security_master
  (symbol, security_name, isin, security_type, currency, exchange_name, status)
VALUES
  ('RELIANCE', 'Reliance Industries Limited', 'INE002A01018', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('TCS', 'Tata Consultancy Services Limited', 'INE467B01029', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('HDFCBANK', 'HDFC Bank Limited', 'INE040A01034', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('INFY', 'Infosys Limited', 'INE009A01021', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('ICICIBANK', 'ICICI Bank Limited', 'INE090A01021', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('SBIN', 'State Bank of India', 'INE062A01020', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('LTIM', 'LTIMindtree Limited', 'INE214T01019', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('SUNPHARMA', 'Sun Pharmaceutical Industries Limited', 'INE044A01036', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('WIPRO', 'Wipro Limited', 'INE075A01022', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('AXISBANK', 'Axis Bank Limited', 'INE238A01034', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('BAJFINANCE', 'Bajaj Finance Limited', 'INE296A01032', 'EQUITY', 'INR', 'NSE', 'ACTIVE'),
  ('MARUTI', 'Maruti Suzuki India Limited', 'INE585B01010', 'EQUITY', 'INR', 'NSE', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  symbol = VALUES(symbol), security_name = VALUES(security_name),
  security_type = VALUES(security_type), currency = VALUES(currency),
  exchange_name = VALUES(exchange_name), status = VALUES(status);

-- One deterministic simulated close per weekday for the six months through today.
INSERT IGNORE INTO security_price
  (price_reference, security_id, market_price, price_timestamp, source_name)
WITH RECURSIVE calendar(price_day) AS (
  SELECT DATE_SUB(CURRENT_DATE(), INTERVAL 183 DAY)
  UNION ALL
  SELECT DATE_ADD(price_day, INTERVAL 1 DAY)
  FROM calendar
  WHERE price_day < CURRENT_DATE()
), trading_days AS (
  SELECT price_day,
         DATEDIFF(price_day, DATE_SUB(CURRENT_DATE(), INTERVAL 183 DAY)) AS day_number
  FROM calendar
  WHERE WEEKDAY(price_day) < 5
), base_prices(symbol, base_price, six_month_change) AS (
  SELECT 'RELIANCE', 2734.20, 0.045 UNION ALL
  SELECT 'TCS', 3985.00, 0.065 UNION ALL
  SELECT 'HDFCBANK', 1643.50, 0.038 UNION ALL
  SELECT 'INFY', 1518.90, -0.020 UNION ALL
  SELECT 'ICICIBANK', 1282.00, 0.057 UNION ALL
  SELECT 'SBIN', 612.40, 0.071 UNION ALL
  SELECT 'LTIM', 5260.10, 0.082 UNION ALL
  SELECT 'SUNPHARMA', 1842.60, 0.034 UNION ALL
  SELECT 'WIPRO', 294.75, -0.018 UNION ALL
  SELECT 'AXISBANK', 1197.30, 0.052 UNION ALL
  SELECT 'BAJFINANCE', 1024.80, 0.061 UNION ALL
  SELECT 'MARUTI', 10645.50, 0.029
)
SELECT UUID(), sm.security_id,
       ROUND(bp.base_price * (1 - bp.six_month_change
         + bp.six_month_change * td.day_number / 184.0
         + 0.012 * SIN(td.day_number * 0.105)
         + 0.004 * SIN(td.day_number * 0.39)), 6),
       TIMESTAMP(td.price_day, '14:00:00.000000'), 'DEMO_SIMULATED'
FROM trading_days td
CROSS JOIN base_prices bp
JOIN security_master sm ON sm.symbol = bp.symbol;
