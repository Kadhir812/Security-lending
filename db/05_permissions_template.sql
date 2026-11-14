-- DBA template only: replace all example passwords before running.
-- Application users have no permissions on other service databases.
CREATE USER 'security_market_db_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_UNIQUE_SECRET';
GRANT SELECT, INSERT, UPDATE ON security_market_db.* TO 'security_market_db_app'@'localhost';
CREATE USER 'custodian_db_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_UNIQUE_SECRET';
GRANT SELECT, INSERT, UPDATE ON custodian_db.* TO 'custodian_db_app'@'localhost';
CREATE USER 'borrower_db_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_UNIQUE_SECRET';
GRANT SELECT, INSERT, UPDATE ON borrower_db.* TO 'borrower_db_app'@'localhost';
CREATE USER 'valuation_accrual_db_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_UNIQUE_SECRET';
GRANT SELECT, INSERT, UPDATE ON valuation_accrual_db.* TO 'valuation_accrual_db_app'@'localhost';
-- Use separate migration credentials. Retention deletes are a controlled operational task.
