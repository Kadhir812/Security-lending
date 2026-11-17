-- One-time Borrower-to-Custodian identity link tokens; MySQL 8.0.16+.
USE custodian_db;

CREATE TABLE IF NOT EXISTS borrower_identity_link (
  jti CHAR(36) NOT NULL PRIMARY KEY,
  organization_reference VARCHAR(80) NOT NULL,
  consumed_by_custodian_id BIGINT NOT NULL,
  consumed_by_user_id BIGINT NOT NULL,
  consumed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  FOREIGN KEY (organization_reference) REFERENCES organization(organization_reference),
  FOREIGN KEY (consumed_by_custodian_id) REFERENCES organization(organization_id),
  FOREIGN KEY (consumed_by_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;
