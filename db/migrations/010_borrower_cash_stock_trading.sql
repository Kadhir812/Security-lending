-- Expand the existing borrower trade activity ledger for cash-funded stock positions.
-- Existing short-sale rows remain valid; newly recorded rows carry the borrower and ISIN.
ALTER TABLE borrower_trade_activity
  MODIFY transaction_reference VARCHAR(80) NULL,
  MODIFY trade_type ENUM('BUY','SELL','SELL_SHORT','BUY_TO_COVER') NOT NULL,
  ADD COLUMN borrower_organization_reference VARCHAR(80) NULL AFTER transaction_reference,
  ADD COLUMN security_isin CHAR(12) NULL AFTER borrower_organization_reference;

UPDATE borrower_trade_activity trade
JOIN users borrower_user ON borrower_user.user_id = trade.created_by
SET trade.borrower_organization_reference = borrower_user.organization_reference
WHERE trade.borrower_organization_reference IS NULL;

UPDATE borrower_trade_activity trade
JOIN loan_contract_view contract
  ON contract.transaction_reference = trade.transaction_reference
JOIN loan_request request
  ON request.request_reference = contract.request_reference
SET trade.security_isin = request.security_isin
WHERE trade.security_isin IS NULL;

CREATE INDEX idx_borrower_trade_positions
  ON borrower_trade_activity (borrower_organization_reference, security_isin, trade_id);
