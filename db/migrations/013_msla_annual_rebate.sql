ALTER TABLE msla_agreement
  ADD COLUMN agreed_annual_rebate_pct DECIMAL(10,6) NOT NULL DEFAULT 0
  AFTER collateral_coverage_pct;
