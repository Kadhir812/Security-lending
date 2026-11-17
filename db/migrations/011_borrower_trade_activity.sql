-- Add Borrower trade executions linked to their originating orders.
-- Execution rows are created only after a trusted execution report is received.

USE borrower_db;

CREATE TABLE borrower_trade_activity (
  version BIGINT NOT NULL DEFAULT 0,
  trade_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  trade_reference VARCHAR(80) NOT NULL UNIQUE,
  order_reference VARCHAR(80),
  transaction_reference VARCHAR(80) NOT NULL,
  trade_type ENUM('SELL_SHORT','BUY_TO_COVER') NOT NULL,
  quantity BIGINT NOT NULL,
  price DECIMAL(20,6) NOT NULL,
  realized_profit_loss DECIMAL(20,2),
  trade_time DATETIME(6) NOT NULL,
  created_by BIGINT NOT NULL,
  CHECK (quantity > 0 AND price > 0),
  FOREIGN KEY (created_by) REFERENCES users(user_id),
  FOREIGN KEY (order_reference) REFERENCES borrower_order(order_reference)
) ENGINE=InnoDB;
