-- Securities Lending Schema (28 Tables)
-- Generated for ER Diagram
CREATE DATABASE IF NOT EXISTS securities_lending;
USE securities_lending;

CREATE TABLE organization (
 organization_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 organization_name VARCHAR(255) NOT NULL,
 organization_type ENUM('LENDER','BORROWER','CUSTODIAN') NOT NULL,
 status ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
 user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 organization_id BIGINT NOT NULL,
 username VARCHAR(100) NOT NULL,
 email VARCHAR(255) UNIQUE,
 phone VARCHAR(50),
 password_hash VARCHAR(500),
 status ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE',
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (organization_id) REFERENCES organization(organization_id)
);

CREATE TABLE roles (
 role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 role_name VARCHAR(100) UNIQUE NOT NULL,
 description VARCHAR(255)
);

CREATE TABLE user_roles (
 user_role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 role_id BIGINT NOT NULL,
 assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES users(user_id),
 FOREIGN KEY(role_id) REFERENCES roles(role_id)
);

CREATE TABLE security_master (
 security_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 symbol VARCHAR(50) UNIQUE NOT NULL,
 security_name VARCHAR(255) NOT NULL,
 isin VARCHAR(50) UNIQUE,
 security_type ENUM('EQUITY','CORPORATE_BOND','GOVT_BOND','ETF'),
 currency VARCHAR(20),
 exchange_name VARCHAR(100),
 status ENUM('ACTIVE','INACTIVE') DEFAULT 'ACTIVE'
);

CREATE TABLE security_price (
 price_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 security_id BIGINT NOT NULL,
 market_price DECIMAL(20,4),
 price_timestamp TIMESTAMP,
 source_name VARCHAR(100),
 FOREIGN KEY(security_id) REFERENCES security_master(security_id)
);

CREATE TABLE msla_agreement (
 msla_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 msla_reference VARCHAR(50) UNIQUE NOT NULL,
 lender_org_id BIGINT NOT NULL,
 borrower_org_id BIGINT NOT NULL,
 effective_date DATE,
 expiry_date DATE,
 agreement_status ENUM('ACTIVE','INACTIVE','EXPIRED'),
 eligible_securities TEXT,
 eligible_collateral TEXT,
 haircut_rules TEXT,
 maximum_exposure DECIMAL(20,2),
 mtm_frequency VARCHAR(100),
 margin_call_window_hours INT,
 recall_rule VARCHAR(100),
 lending_fee_rate DECIMAL(10,4),
 default_rules TEXT,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(lender_org_id) REFERENCES organization(organization_id),
 FOREIGN KEY(borrower_org_id) REFERENCES organization(organization_id)
);

CREATE TABLE counterparty_credit_limit (
 credit_limit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 msla_id BIGINT NOT NULL,
 limit_amount DECIMAL(20,2),
 used_amount DECIMAL(20,2),
 available_amount DECIMAL(20,2),
 status ENUM('ACTIVE','INACTIVE'),
 FOREIGN KEY(msla_id) REFERENCES msla_agreement(msla_id)
);

CREATE TABLE credit_limit_history (
 history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 credit_limit_id BIGINT NOT NULL,
 old_limit DECIMAL(20,2),
 new_limit DECIMAL(20,2),
 changed_by BIGINT,
 changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(credit_limit_id) REFERENCES counterparty_credit_limit(credit_limit_id),
 FOREIGN KEY(changed_by) REFERENCES users(user_id)
);

CREATE TABLE collateral_haircut (
 haircut_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 msla_id BIGINT NOT NULL,
 security_type VARCHAR(100),
 haircut_percentage DECIMAL(10,2),
 effective_date DATE,
 FOREIGN KEY(msla_id) REFERENCES msla_agreement(msla_id)
);

CREATE TABLE lender_inventory (
 inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 organization_id BIGINT NOT NULL,
 security_id BIGINT NOT NULL,
 total_quantity BIGINT NOT NULL,
 on_loan_quantity BIGINT DEFAULT 0,
 UNIQUE (organization_id,security_id),
 FOREIGN KEY(organization_id) REFERENCES organization(organization_id),
 FOREIGN KEY(security_id) REFERENCES security_master(security_id)
);

CREATE TABLE locate_request (
 locate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 borrower_id BIGINT NOT NULL,
 security_id BIGINT NOT NULL,
 quantity BIGINT,
 request_time TIMESTAMP NULL,
 expiry_time TIMESTAMP NULL,
 status ENUM('PENDING','APPROVED','REJECTED','USED','EXPIRED'),
 FOREIGN KEY(borrower_id) REFERENCES organization(organization_id),
 FOREIGN KEY(security_id) REFERENCES security_master(security_id)
);

CREATE TABLE loan_request (
 request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 borrower_id BIGINT NOT NULL,
 security_id BIGINT NOT NULL,
 quantity BIGINT,
 requested_date TIMESTAMP NULL,
 status ENUM('PENDING','APPROVED','REJECTED'),
 FOREIGN KEY(borrower_id) REFERENCES organization(organization_id),
 FOREIGN KEY(security_id) REFERENCES security_master(security_id)
);

CREATE TABLE loan_contract (
 contract_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 transaction_reference VARCHAR(50) UNIQUE NOT NULL,
 msla_id BIGINT NOT NULL,
 loan_request_id BIGINT,
 lender_org_id BIGINT NOT NULL,
 borrower_org_id BIGINT NOT NULL,
 security_id BIGINT NOT NULL,
 quantity BIGINT NOT NULL,
 market_price DECIMAL(20,4),
 loan_value DECIMAL(20,2),
 haircut_percentage DECIMAL(10,2),
 required_collateral DECIMAL(20,2),
 posted_collateral DECIMAL(20,2),
 latest_coverage_ratio DECIMAL(10,2),
 collateral_type VARCHAR(50),
 rebate_rate DECIMAL(10,4),
 trade_date DATE,
 settlement_date DATE,
 recall_conditions VARCHAR(255),
 contract_status ENUM('REQUESTED','APPROVED','PENDING_COLLATERAL','ON_LOAN','RECALLED','RETURNED','DEFAULTED','CLOSED'),
 contract_created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(msla_id) REFERENCES msla_agreement(msla_id),
 FOREIGN KEY(loan_request_id) REFERENCES loan_request(request_id),
 FOREIGN KEY(lender_org_id) REFERENCES organization(organization_id),
 FOREIGN KEY(borrower_org_id) REFERENCES organization(organization_id),
 FOREIGN KEY(security_id) REFERENCES security_master(security_id)
);

CREATE TABLE borrower_inventory (
 inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 borrower_id BIGINT NOT NULL,
 contract_id BIGINT,
 security_id BIGINT NOT NULL,
 borrowed_quantity BIGINT NOT NULL,
 UNIQUE (borrower_id,contract_id,security_id),
 FOREIGN KEY(borrower_id) REFERENCES organization(organization_id),
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id),
 FOREIGN KEY(security_id) REFERENCES security_master(security_id)
);

CREATE TABLE inventory_movement (
 movement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 security_id BIGINT NOT NULL,
 from_organization_id BIGINT,
 to_organization_id BIGINT,
 quantity BIGINT,
 movement_type ENUM('LOAN_DELIVERY','RETURN','RECALL_RETURN','TRANSFER'),
 movement_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(security_id) REFERENCES security_master(security_id),
 FOREIGN KEY(from_organization_id) REFERENCES organization(organization_id),
 FOREIGN KEY(to_organization_id) REFERENCES organization(organization_id)
);

CREATE TABLE contract_status_history (
 history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contract_id BIGINT NOT NULL,
 old_status VARCHAR(50),
 new_status VARCHAR(50),
 changed_by BIGINT,
 changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id),
 FOREIGN KEY(changed_by) REFERENCES users(user_id)
);

CREATE TABLE collateral (
 collateral_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contract_id BIGINT NOT NULL,
 collateral_type ENUM('CASH','TREASURY','EQUITY'),
 collateral_value DECIMAL(20,2),
 posted_date TIMESTAMP NULL,
 status ENUM('POSTED','RELEASED'),
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id)
);

CREATE TABLE collateral_valuation_history (
 valuation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 collateral_id BIGINT NOT NULL,
 market_value DECIMAL(20,2),
 valuation_date TIMESTAMP,
 FOREIGN KEY(collateral_id) REFERENCES collateral(collateral_id)
);

CREATE TABLE mark_to_market_snapshot (
 snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contract_id BIGINT NOT NULL,
 market_price DECIMAL(20,4),
 loan_value DECIMAL(20,2),
 required_collateral DECIMAL(20,2),
 current_collateral DECIMAL(20,2),
 coverage_ratio DECIMAL(10,2),
 snapshot_time TIMESTAMP,
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id)
);

CREATE TABLE margin_call (
 margin_call_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contract_id BIGINT NOT NULL,
 required_collateral DECIMAL(20,2),
 current_collateral DECIMAL(20,2),
 shortfall_amount DECIMAL(20,2),
 coverage_ratio DECIMAL(10,2),
 status ENUM('OPEN','SATISFIED','FAILED','BREACHED','CANCELLED'),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id)
);

CREATE TABLE margin_call_transaction_log (
 log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 margin_call_id BIGINT NOT NULL,
 activity_type VARCHAR(100),
 amount DECIMAL(20,2),
 activity_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(margin_call_id) REFERENCES margin_call(margin_call_id)
);

CREATE TABLE recall_request (
 recall_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contract_id BIGINT NOT NULL,
 recall_date DATE,
 due_date DATE,
 recall_reason VARCHAR(500),
 recall_status ENUM('OPEN','PROCESSING','COMPLETED','OVERDUE','DEFAULTED'),
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id)
);

CREATE TABLE settlement_instruction (
 instruction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contract_id BIGINT NOT NULL,
 instruction_type ENUM('SECURITY_DELIVERY','COLLATERAL_TRANSFER','SECURITY_RETURN'),
 from_account VARCHAR(255),
 to_account VARCHAR(255),
 quantity BIGINT,
 amount DECIMAL(20,2),
 status ENUM('PENDING','SETTLED','FAILED'),
 settlement_date DATE,
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id)
);

CREATE TABLE borrower_trade_activity (
 trade_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 borrower_id BIGINT NOT NULL,
 security_id BIGINT NOT NULL,
 contract_id BIGINT,
 trade_type ENUM('SELL_SHORT','BUY_TO_COVER'),
 quantity BIGINT,
 price DECIMAL(20,4),
 profit_loss DECIMAL(20,2),
 trade_date TIMESTAMP,
 FOREIGN KEY(borrower_id) REFERENCES organization(organization_id),
 FOREIGN KEY(security_id) REFERENCES security_master(security_id),
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id)
);

CREATE TABLE rebate_calculation (
 rebate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contract_id BIGINT NOT NULL,
 loan_value DECIMAL(20,2),
 rebate_rate DECIMAL(10,4),
 calculated_amount DECIMAL(20,2),
 calculation_date DATE,
 FOREIGN KEY(contract_id) REFERENCES loan_contract(contract_id)
);

CREATE TABLE rebate_payment_history (
 payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 rebate_id BIGINT NOT NULL,
 payment_amount DECIMAL(20,2),
 payment_date DATE,
 status ENUM('PENDING','PAID','FAILED'),
 FOREIGN KEY(rebate_id) REFERENCES rebate_calculation(rebate_id)
);

CREATE TABLE lending_fee (
 fee_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 msla_id BIGINT NOT NULL,
 security_id BIGINT,
 fee_percentage DECIMAL(10,4),
 effective_from DATE,
 effective_to DATE,
 status ENUM('ACTIVE','INACTIVE'),
 FOREIGN KEY(msla_id) REFERENCES msla_agreement(msla_id),
 FOREIGN KEY(security_id) REFERENCES security_master(security_id)
);

CREATE INDEX idx_contract_status ON loan_contract(contract_status);
CREATE INDEX idx_margin_status ON margin_call(status);
CREATE INDEX idx_recall_status ON recall_request(recall_status);
CREATE INDEX idx_security_price_time ON security_price(security_id, price_timestamp);
