-- Apply to borrower_db only. The table stores the Custodian-approved contract snapshot.
USE borrower_db;

CREATE TABLE loan_contract_view (
  contract_view_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_reference VARCHAR(80) NOT NULL UNIQUE,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_snapshot JSON NOT NULL,
  received_at DATETIME(6) NOT NULL,
  FOREIGN KEY (request_reference) REFERENCES loan_request(request_reference)
) ENGINE=InnoDB;
