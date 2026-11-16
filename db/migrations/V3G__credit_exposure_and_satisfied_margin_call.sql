-- Apply once to an existing Custodian database; fresh installations use
-- 02_custodian_db.sql. This preserves the 50-table maximum.
ALTER TABLE margin_call
  DROP CHECK margin_call_chk_1,
  DROP COLUMN shortfall_amount,
  ADD COLUMN shortfall_amount DECIMAL(20,2)
    GENERATED ALWAYS AS (GREATEST(required_collateral-current_collateral, 0)) STORED,
  ADD CONSTRAINT chk_margin_call_nonnegative_current CHECK (current_collateral >= 0);

CREATE TABLE credit_exposure_history (
  version BIGINT NOT NULL DEFAULT 0,
  exposure_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  credit_limit_id BIGINT NOT NULL,
  contract_id BIGINT NOT NULL,
  valuation_result_reference CHAR(36) NOT NULL UNIQUE,
  idempotency_key VARCHAR(80) NOT NULL,
  input_hash CHAR(64) NOT NULL,
  old_used_amount DECIMAL(20,2) NOT NULL,
  new_used_amount DECIMAL(20,2) NOT NULL,
  old_available_amount DECIMAL(20,2) NOT NULL,
  new_available_amount DECIMAL(20,2) NOT NULL,
  breach_created BOOLEAN NOT NULL,
  reason VARCHAR(500) NOT NULL,
  changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (contract_id, idempotency_key),
  CHECK (old_used_amount >= 0 AND new_used_amount >= 0),
  FOREIGN KEY (credit_limit_id) REFERENCES counterparty_credit_limit(credit_limit_id),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) ENGINE=InnoDB;
