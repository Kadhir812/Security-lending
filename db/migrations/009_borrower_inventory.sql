-- Add and seed the Borrower-owned position read model in existing databases.
-- MySQL 8.0.16+; existing borrower_db data is preserved.

USE borrower_db;

CREATE TABLE borrower_inventory (
  borrower_organization_reference VARCHAR(80) NOT NULL,
  source_contract_version BIGINT NOT NULL DEFAULT 0,
  inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  security_isin CHAR(12) NOT NULL,
  borrowed_quantity BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (borrowed_quantity >= 0)
) ENGINE=InnoDB;

INSERT INTO borrower_inventory (
  borrower_organization_reference,
  source_contract_version,
  transaction_reference,
  security_isin,
  borrowed_quantity
)
SELECT
  JSON_UNQUOTE(JSON_EXTRACT(contract_snapshot, '$.borrowerOrganizationReference')),
  COALESCE(CAST(JSON_UNQUOTE(
    JSON_EXTRACT(contract_snapshot, '$.settlementContractVersion')) AS UNSIGNED), 0),
  transaction_reference,
  JSON_UNQUOTE(JSON_EXTRACT(contract_snapshot, '$.securityIsin')),
  CAST(JSON_UNQUOTE(
    JSON_EXTRACT(contract_snapshot, '$.outstandingQuantity')) AS UNSIGNED)
FROM loan_contract_view
WHERE CAST(COALESCE(JSON_UNQUOTE(
    JSON_EXTRACT(contract_snapshot, '$.outstandingQuantity')), '0') AS UNSIGNED) > 0
  AND JSON_UNQUOTE(JSON_EXTRACT(contract_snapshot, '$.borrowerOrganizationReference')) IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(contract_snapshot, '$.securityIsin')) IS NOT NULL;
