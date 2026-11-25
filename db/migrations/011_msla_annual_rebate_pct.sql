-- Persist the MSLA-level annual rebate rate used by daily collateral rebate calculations.
ALTER TABLE msla_agreement
  ADD COLUMN annual_rebate_pct DECIMAL(10,6) NOT NULL DEFAULT 0
  AFTER collateral_coverage_pct;

ALTER TABLE msla_agreement
  ADD CONSTRAINT chk_msla_annual_rebate_pct_nonnegative
  CHECK (annual_rebate_pct >= 0);

ALTER TABLE loan_contract
  ADD COLUMN agreed_annual_rebate_pct DECIMAL(10,6) NOT NULL DEFAULT 0
  AFTER agreed_annual_fee_pct;

ALTER TABLE loan_contract
  ADD CONSTRAINT chk_contract_annual_rebate_pct_nonnegative
  CHECK (agreed_annual_rebate_pct >= 0);

CREATE TABLE calculation_schedule_config (
  schedule_key VARCHAR(40) PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  interval_minutes INT NOT NULL DEFAULT 60,
  next_run_at DATETIME(6) NOT NULL,
  last_run_at DATETIME(6),
  last_posted_count INT NOT NULL DEFAULT 0,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (interval_minutes BETWEEN 5 AND 1440),
  CHECK (last_posted_count >= 0)
) ENGINE=InnoDB;

INSERT INTO calculation_schedule_config
  (schedule_key, interval_minutes, next_run_at, last_posted_count)
VALUES ('CUSTODIAN_ACCRUAL', 60, DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 60 MINUTE), 0);
