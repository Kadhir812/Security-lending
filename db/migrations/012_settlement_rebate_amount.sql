-- Persist the accumulated rebate paid with a confirmed final collateral release.
ALTER TABLE settlement_instruction
  ADD COLUMN rebate_amount DECIMAL(20,2) NOT NULL DEFAULT 0
  AFTER amount;

ALTER TABLE settlement_instruction
  ADD CONSTRAINT chk_settlement_rebate_amount_nonnegative
  CHECK (rebate_amount >= 0);
