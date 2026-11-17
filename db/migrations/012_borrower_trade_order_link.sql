-- Link each Borrower trade execution to its originating order.
-- Apply only after borrower_order and borrower_trade_activity exist.
-- Historical trades remain valid with a NULL order_reference.

USE borrower_db;

ALTER TABLE borrower_trade_activity
  ADD COLUMN order_reference VARCHAR(80) NULL AFTER trade_reference,
  ADD CONSTRAINT fk_borrower_trade_activity_order
    FOREIGN KEY (order_reference) REFERENCES borrower_order(order_reference);
