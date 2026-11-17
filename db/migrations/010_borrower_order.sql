-- Add local Borrower order capture to existing borrower_db installations.
-- Orders are stored as NEW until an execution integration updates their status.

USE borrower_db;

CREATE TABLE borrower_order (
  order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_reference VARCHAR(80) NOT NULL UNIQUE,
  borrower_organization_reference VARCHAR(80) NOT NULL,
  security_isin CHAR(12) NOT NULL,
  transaction_reference VARCHAR(80),
  side ENUM('BUY','SELL') NOT NULL,
  order_quantity BIGINT NOT NULL,
  filled_quantity BIGINT NOT NULL DEFAULT 0,
  leaves_quantity BIGINT NOT NULL,
  average_fill_price DECIMAL(20,6),
  order_status ENUM('NEW','PARTIALLY_FILLED','FILLED','CANCELLED','REJECTED','EXPIRED') NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (order_quantity > 0 AND filled_quantity BETWEEN 0 AND order_quantity),
  CHECK (leaves_quantity >= 0 AND filled_quantity + leaves_quantity <= order_quantity),
  CHECK (order_status NOT IN ('FILLED','CANCELLED','REJECTED','EXPIRED') OR leaves_quantity = 0),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;
