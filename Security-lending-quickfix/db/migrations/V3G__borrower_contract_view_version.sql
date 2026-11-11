-- Apply once to an existing Borrower database. Fresh installations use
-- 03_borrower_db.sql.
ALTER TABLE loan_contract_view
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
