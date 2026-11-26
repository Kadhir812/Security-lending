-- Phase 3D additive migration for existing databases. Apply ONCE before deployment.
-- Existing request rows remain NULL; new API submissions always set the MSLA currency.
-- MySQL DDL commits implicitly. Back up first; apply using a deployment account.

USE borrower_db;
ALTER TABLE loan_request
  ADD COLUMN settlement_currency CHAR(3) NULL;

USE custodian_db;
ALTER TABLE loan_request
  ADD COLUMN settlement_currency CHAR(3) NULL;
