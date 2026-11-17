ALTER TABLE security_master
  ADD COLUMN sedol VARCHAR(7) NULL,
  ADD COLUMN sector VARCHAR(100) NULL,
  ADD COLUMN lot_size BIGINT NOT NULL DEFAULT 1,
  ADD CONSTRAINT chk_security_master_lot_size CHECK (lot_size > 0);

INSERT INTO security_master (
  version, symbol, security_name, isin, security_type, currency, exchange_name,
  status, sedol, sector, lot_size
) VALUES
  (0, 'INFY', 'Infosys Limited', 'INE009A01021', 'EQUITY', 'INR', 'NSE',
   'ACTIVE', '6205122', 'IT Services', 1),
  (0, 'TCS', 'Tata Consultancy Services Ltd.', 'INE467B01029', 'EQUITY', 'INR', 'NSE',
   'ACTIVE', 'B01NPJ1', 'IT Services', 1)
ON DUPLICATE KEY UPDATE
  sedol = VALUES(sedol),
  sector = VALUES(sector),
  lot_size = VALUES(lot_size);
