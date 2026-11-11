-- Securities lending: final 35-table logical schema / MySQL 8.0.16+ syntax.
-- Fresh database creation script, NOT an ALTER migration for existing tables.
-- Use UTC for all DATETIME values. Rates are percentages: 2.0 means 2%.
-- Scope: whole-unit loan securities, one request -> one contract, optional one-use locate.
-- MVP uses one currency per MSLA, loan and collateral; backend rejects cross-currency trades.
-- Cash coverage (e.g. 105%) and collateral valuation haircut are distinct concepts.
-- The source brief names PostgreSQL/H2. This file intentionally follows the user's MySQL DDL.
--
-- NORMALIZATION / ROUTING
-- request.msla_id -> msla.borrower_org_id and msla.mandate_id
-- mandate -> lender_org_id, custodian_org_id. Receiver is resolved without duplicate party IDs.
-- contract.loan_request_id -> agreement, parties and security. Accepted requests are immutable.
--
-- SERVICE BOUNDARY
-- This is one logical ER schema for the training application, not two production DB deployments.
-- Custodian owns agreements, limits, lending inventory, contracts, collateral and settlements.
-- Borrower owns its submitted requests/trades and consumes custodian events into local views.
-- In separate DBs, replace cross-service FKs with external IDs and local read models;
-- install inbox/outbox/message tables in each service DB. Never share write ownership.
--
-- REQUIRED TRANSACTIONAL BACKEND RULES (not enforceable by row CHECK constraints):
-- 1. Validate organization roles, user membership, active mandate/MSLA and validity dates.
-- 2. Validate the selected security_master.security_type against
--    msla_agreement.eligible_securities.allowed_types. Validate matching MSLA/security
--    across locate/request and all contract-linked child rows.
-- 3. Choose one effective fee/haircut rule; reject ambiguous overlapping periods.
--    For the current CASH-only collateral workflow, snapshot a validated 0% CASH haircut.
-- 4. Lock inventory and credit rows in consistent order; reserve both with contract + outbox.
--    Keep inventory counters equal to reservation/settlement totals. Use version checks too.
-- 5. Validate all state transitions. Count only confirmed settlement; no double settlement.
--    Settlement child IDs must belong to the same contract; asset, currency and quantity
--    must match. Prevent total pending + settled deliveries/returns/releases exceeding balances.
-- 6. Process each settlement atomically with movement, reservation, contract, credit,
--    collateral/recall balances, history and outbox. Failed attempts do not move balances.
-- 7. MTM uses consistent as-of prices and settled remaining collateral; preserve old snapshots.
--    Credit exposure here is loan market value; no credit netting by collateral.
--    Limit breaches caused by MTM are allowed to be recorded and block new lending.
-- 8. Allow only one active margin call and one active recall per contract.
--    Active margin-call statuses are OPEN, PARTIALLY_SATISFIED and BREACHED;
--    active recall statuses are OPEN, PROCESSING, PARTIALLY_RETURNED and OVERDUE.
--    A recall must also stay within outstanding unrecalled units.
-- 9. Never release collateral below agreed coverage. Cancel pending transfers before closure.
-- 10. Accrue daily using agreed daily balance convention; prevent overpayment and reverse
--     payer/payee for negative rebates. Close only after inventory, collateral and charges resolve.
-- 11. Treat messages as untrusted: authenticate sender, authorize receiver, validate schema,
--     correlation and version. Deduplicate per consumer; handle out-of-order versions.
-- 12. Keep financial/audit history immutable. No cascading deletes or hard deletion of trades.
-- 13. FIX type mappings must be confirmed against the chosen FIX version and lab agreement;
--     do not infer a FIX type solely from a table name. QuickFIX/J manages session persistence.
-- 14. Before approving a loan request, require requested quantity <= lender inventory
--     available_quantity for the matching security. Lock the inventory row and reserve
--     the quantity atomically; reject requests that exceed lender inventory.
-- 15. Before reserving credit, require new_reservation_amount <= available_amount.
--     Equivalently, used_amount + reserved_amount + new_reservation_amount <= limit_amount.
--     Lock the credit row and reserve atomically with contract creation, inventory
--     reservation and the outbox event; reject credit-limit breaches.
-- 16. Build and validate loan_contract.collateral_terms from the selected MSLA collateral
--     conditions and effective collateral_haircut rule. The current workflow accepts CASH;
--     other collateral types remain represented for future support.

CREATE DATABASE IF NOT EXISTS securities_lending
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE securities_lending;
SET time_zone = '+00:00';

-- 1. organization
CREATE TABLE organization (
  organization_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_name VARCHAR(255) NOT NULL,
  organization_type ENUM('LENDER','BORROWER','CUSTODIAN') NOT NULL,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

-- 2. users
CREATE TABLE users (
  user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  username VARCHAR(100) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  phone VARCHAR(50),
  password_hash VARCHAR(500) NOT NULL,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (organization_id, username),
  FOREIGN KEY (organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- 3. roles
CREATE TABLE roles (
  role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255)
) ENGINE=InnoDB;

-- 4. user_roles
CREATE TABLE user_roles (
  user_role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES users(user_id),
  FOREIGN KEY (role_id) REFERENCES roles(role_id)
) ENGINE=InnoDB;

-- 5. lender_custodian_mandate
CREATE TABLE lender_custodian_mandate (
  mandate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lender_org_id BIGINT NOT NULL,
  custodian_org_id BIGINT NOT NULL,
  mandate_reference VARCHAR(80) NOT NULL UNIQUE,
  effective_from DATE NOT NULL,
  effective_to DATE,
  status ENUM('ACTIVE','INACTIVE','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  CHECK (lender_org_id <> custodian_org_id),
  CHECK (effective_to IS NULL OR effective_to >= effective_from),
  FOREIGN KEY (lender_org_id) REFERENCES organization(organization_id),
  FOREIGN KEY (custodian_org_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- 6. security_master
-- Instrument identified by ISIN. Symbol is not globally unique.
CREATE TABLE security_master (
  security_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol VARCHAR(50) NOT NULL,
  security_name VARCHAR(255) NOT NULL,
  isin CHAR(12) NOT NULL UNIQUE,
  security_type ENUM('EQUITY','CORPORATE_BOND','GOVT_BOND','ETF') NOT NULL,
  currency CHAR(3) NOT NULL,
  exchange_name VARCHAR(100),
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  INDEX idx_security_symbol (symbol)
) ENGINE=InnoDB;

-- 7. security_price
CREATE TABLE security_price (
  price_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  security_id BIGINT NOT NULL,
  market_price DECIMAL(20,6) NOT NULL,
  price_timestamp DATETIME(6) NOT NULL,
  source_name VARCHAR(100) NOT NULL,
  CHECK (market_price > 0),
  UNIQUE (security_id, price_timestamp, source_name),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id)
) ENGINE=InnoDB;

-- 8. msla_agreement
-- Lender/custodian derive from mandate. eligible_securities stores allowed asset types,
-- not individual security IDs; its JSON structure and values are validated by backend.
-- collateral_type is the current default collateral condition; backend currently accepts CASH.
CREATE TABLE msla_agreement (
  msla_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msla_reference VARCHAR(80) NOT NULL UNIQUE,
  mandate_id BIGINT NOT NULL,
  borrower_org_id BIGINT NOT NULL,
  effective_date DATE NOT NULL,
  expiry_date DATE,
  agreement_status ENUM('DRAFT','ACTIVE','INACTIVE','EXPIRED') NOT NULL DEFAULT 'DRAFT',
  base_currency CHAR(3) NOT NULL,
  eligible_securities JSON NOT NULL,
  collateral_type ENUM('CASH','ETF','GOVT_BOND','CORPORATE_BOND','EQUITY') NOT NULL DEFAULT 'CASH',
  collateral_coverage_pct DECIMAL(9,4) NOT NULL DEFAULT 105.0000,
  mtm_interval_minutes INT NOT NULL DEFAULT 60,
  margin_call_window_hours INT NOT NULL DEFAULT 24,
  recall_rule VARCHAR(500),
  default_rules JSON,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (expiry_date IS NULL OR expiry_date >= effective_date),
  CHECK (collateral_coverage_pct >= 100),
  CHECK (mtm_interval_minutes > 0 AND margin_call_window_hours > 0),
  FOREIGN KEY (mandate_id) REFERENCES lender_custodian_mandate(mandate_id),
  FOREIGN KEY (borrower_org_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- 9. counterparty_credit_limit
-- Currency derives from MSLA. Negative available credit is allowed after MTM; block new acceptance.
CREATE TABLE counterparty_credit_limit (
  credit_limit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msla_id BIGINT NOT NULL UNIQUE,
  limit_amount DECIMAL(20,2) NOT NULL,
  used_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  reserved_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  available_amount DECIMAL(20,2) GENERATED ALWAYS AS (limit_amount-used_amount-reserved_amount) STORED,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (limit_amount >= 0 AND used_amount >= 0 AND reserved_amount >= 0),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id)
) ENGINE=InnoDB;

-- 10. credit_limit_history
CREATE TABLE credit_limit_history (
  history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  credit_limit_id BIGINT NOT NULL,
  old_limit DECIMAL(20,2) NOT NULL,
  new_limit DECIMAL(20,2) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  changed_by BIGINT NOT NULL,
  changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (old_limit >= 0 AND new_limit >= 0),
  FOREIGN KEY (credit_limit_id) REFERENCES counterparty_credit_limit(credit_limit_id),
  FOREIGN KEY (changed_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 11. collateral_haircut
-- Valuation haircut is separate from required coverage percentage. Backend prevents overlapping rules.
CREATE TABLE collateral_haircut (
  haircut_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msla_id BIGINT NOT NULL,
  collateral_type ENUM('CASH','GOVT_BOND','CORPORATE_BOND','EQUITY','ETF') NOT NULL,
  currency CHAR(3) NOT NULL,
  haircut_percentage DECIMAL(9,4) NOT NULL,
  effective_from DATETIME(6) NOT NULL,
  effective_to DATETIME(6),
  UNIQUE (msla_id, collateral_type, currency, effective_from),
  CHECK (haircut_percentage >= 0 AND haircut_percentage < 100),
  CHECK (effective_to IS NULL OR effective_to > effective_from),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id)
) ENGINE=InnoDB;

-- 12. lending_fee
-- NULL security means agreement default; backend chooses one effective rule and snapshots it on contract.
CREATE TABLE lending_fee (
  fee_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msla_id BIGINT NOT NULL,
  security_id BIGINT,
  annual_fee_pct DECIMAL(10,6) NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE,
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  CHECK (annual_fee_pct >= 0),
  CHECK (effective_to IS NULL OR effective_to >= effective_from),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id)
) ENGINE=InnoDB;

-- 13. lender_inventory
CREATE TABLE lender_inventory (
  inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NOT NULL,
  security_id BIGINT NOT NULL,
  total_quantity BIGINT NOT NULL,
  on_loan_quantity BIGINT NOT NULL DEFAULT 0,
  reserved_quantity BIGINT NOT NULL DEFAULT 0,
  available_quantity BIGINT GENERATED ALWAYS AS (total_quantity-on_loan_quantity-reserved_quantity) STORED,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (organization_id, security_id),
  CHECK (total_quantity >= 0 AND on_loan_quantity >= 0 AND reserved_quantity >= 0),
  CHECK (on_loan_quantity + reserved_quantity <= total_quantity),
  FOREIGN KEY (organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id)
) ENGINE=InnoDB;

-- 14. locate_request
-- Borrower/lender/receiving custodian resolve through MSLA and mandate; no duplicate party columns.
-- The selected security is validated by backend against MSLA eligible asset types.
CREATE TABLE locate_request (
  locate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  locate_reference VARCHAR(80) NOT NULL UNIQUE,
  msla_id BIGINT NOT NULL,
  security_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL,
  approved_quantity BIGINT,
  created_by BIGINT NOT NULL,
  request_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expiry_time DATETIME(6) NOT NULL,
  responded_at DATETIME(6),
  status ENUM('PENDING','APPROVED','REJECTED','USED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  response_reason VARCHAR(500),
  CHECK (quantity > 0),
  CHECK (approved_quantity IS NULL OR (approved_quantity > 0 AND approved_quantity <= quantity)),
  CHECK (expiry_time > request_time),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 15. loan_request
-- The selected security is validated by backend against MSLA eligibility and the linked locate.
CREATE TABLE loan_request (
  request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_reference VARCHAR(80) NOT NULL UNIQUE,
  msla_id BIGINT NOT NULL,
  locate_id BIGINT UNIQUE,
  security_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL,
  requested_settlement_date DATE NOT NULL,
  created_by BIGINT NOT NULL,
  requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  status ENUM('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
  response_reason VARCHAR(500),
  responded_at DATETIME(6),
  CHECK (quantity > 0),
  FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id),
  FOREIGN KEY (locate_id) REFERENCES locate_request(locate_id),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 16. loan_contract
-- MSLA, security and parties derive from immutable accepted request. Latest values are cached snapshots.
-- collateral_terms is a backend-built snapshot of MSLA collateral conditions and the
-- effective haircut rule; coverage and settlement currency remain dedicated columns.
CREATE TABLE loan_contract (
  contract_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  transaction_reference VARCHAR(80) NOT NULL UNIQUE,
  loan_request_id BIGINT NOT NULL UNIQUE,
  quantity BIGINT NOT NULL,
  delivered_quantity BIGINT NOT NULL DEFAULT 0,
  returned_quantity BIGINT NOT NULL DEFAULT 0,
  outstanding_quantity BIGINT GENERATED ALWAYS AS (delivered_quantity-returned_quantity) STORED,
  settlement_currency CHAR(3) NOT NULL,
  agreed_annual_fee_pct DECIMAL(10,6) NOT NULL,
  agreed_annual_rebate_pct DECIMAL(10,6) NOT NULL DEFAULT 0,
  day_count_basis ENUM('ACT_365','ACT_360') NOT NULL DEFAULT 'ACT_365',
  collateral_coverage_pct DECIMAL(9,4) NOT NULL,
  collateral_terms JSON NOT NULL,
  latest_market_price DECIMAL(20,6),
  latest_loan_value DECIMAL(20,2),
  latest_required_collateral DECIMAL(20,2),
  latest_adjusted_collateral DECIMAL(20,2),
  latest_coverage_pct DECIMAL(12,6),
  latest_valuation_at DATETIME(6),
  reserved_credit_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  used_credit_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  trade_date DATE NOT NULL,
  settlement_date DATE NOT NULL,
  maturity_date DATE,
  recall_conditions VARCHAR(500),
  contract_status ENUM('APPROVED','PENDING_COLLATERAL','PENDING_SETTLEMENT','PARTIALLY_SETTLED','ON_LOAN','RECALLED','RETURNED','DEFAULTED','CANCELLED','SETTLEMENT_FAILED','CLOSED') NOT NULL DEFAULT 'APPROVED',
  version BIGINT NOT NULL DEFAULT 0,
  contract_created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (quantity > 0 AND delivered_quantity >= 0 AND delivered_quantity <= quantity),
  CHECK (returned_quantity >= 0 AND returned_quantity <= delivered_quantity),
  CHECK (agreed_annual_fee_pct >= 0 AND collateral_coverage_pct >= 100),
  CHECK (reserved_credit_amount >= 0 AND used_credit_amount >= 0),
  CHECK (settlement_date >= trade_date),
  CHECK (maturity_date IS NULL OR maturity_date >= settlement_date),
  FOREIGN KEY (loan_request_id) REFERENCES loan_request(request_id)
) ENGINE=InnoDB;

-- 17. inventory_reservation
CREATE TABLE inventory_reservation (
  reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL UNIQUE,
  inventory_id BIGINT NOT NULL,
  reserved_quantity BIGINT NOT NULL,
  consumed_quantity BIGINT NOT NULL DEFAULT 0,
  released_quantity BIGINT NOT NULL DEFAULT 0,
  remaining_quantity BIGINT GENERATED ALWAYS AS (reserved_quantity-consumed_quantity-released_quantity) STORED,
  status ENUM('ACTIVE','CONSUMED','RELEASED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at DATETIME(6) NOT NULL,
  CHECK (reserved_quantity > 0 AND consumed_quantity >= 0 AND released_quantity >= 0),
  CHECK (consumed_quantity + released_quantity <= reserved_quantity),
  CHECK (expires_at > created_at),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (inventory_id) REFERENCES lender_inventory(inventory_id)
) ENGINE=InnoDB;

-- 18. borrower_inventory
-- Borrower identity is derived through contract -> loan_request -> msla_agreement.borrower_org_id.
-- Positions are recorded per contract and can be aggregated by borrower organization and security.
-- This is not the authoritative obligation; loan_contract remains authoritative.
CREATE TABLE borrower_inventory (
  inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL UNIQUE,
  security_id BIGINT NOT NULL,
  borrowed_quantity BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (borrowed_quantity >= 0),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id)
) ENGINE=InnoDB;

-- 19. contract_status_history
CREATE TABLE contract_status_history (
  history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  old_status VARCHAR(40),
  new_status VARCHAR(40) NOT NULL,
  changed_by BIGINT,
  actor_type ENUM('USER','SYSTEM') NOT NULL,
  reason VARCHAR(500) NOT NULL,
  source_event_id CHAR(36),
  changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (actor_type <> 'USER' OR changed_by IS NOT NULL),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (changed_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 20. collateral
-- One collateral lot per posting. Pending cash amount is intended amount; count only confirmed posted lots.
CREATE TABLE collateral (
  collateral_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  collateral_type ENUM('CASH','GOVT_BOND','CORPORATE_BOND','EQUITY','ETF') NOT NULL,
  security_id BIGINT,
  currency CHAR(3) NOT NULL,
  pledged_quantity DECIMAL(24,6),
  released_quantity DECIMAL(24,6) NOT NULL DEFAULT 0,
  posted_cash_amount DECIMAL(20,2),
  released_cash_amount DECIMAL(20,2) NOT NULL DEFAULT 0,
  applied_haircut_pct DECIMAL(9,4) NOT NULL DEFAULT 0,
  posted_at DATETIME(6),
  status ENUM('PENDING','POSTED','PARTIALLY_RELEASED','RELEASED','FAILED') NOT NULL DEFAULT 'PENDING',
  CHECK (applied_haircut_pct >= 0 AND applied_haircut_pct < 100),
  CHECK ((collateral_type = 'CASH' AND security_id IS NULL AND pledged_quantity IS NULL AND released_quantity = 0 AND posted_cash_amount IS NOT NULL AND posted_cash_amount > 0 AND released_cash_amount BETWEEN 0 AND posted_cash_amount)
   OR (collateral_type <> 'CASH' AND security_id IS NOT NULL AND posted_cash_amount IS NULL AND released_cash_amount = 0 AND pledged_quantity IS NOT NULL AND pledged_quantity > 0 AND released_quantity BETWEEN 0 AND pledged_quantity)),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id)
) ENGINE=InnoDB;

-- 21. collateral_valuation_history
CREATE TABLE collateral_valuation_history (
  valuation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  collateral_id BIGINT NOT NULL,
  remaining_quantity DECIMAL(24,6),
  market_price DECIMAL(20,6),
  market_value DECIMAL(20,2) NOT NULL,
  haircut_pct DECIMAL(9,4) NOT NULL,
  adjusted_value DECIMAL(20,2) NOT NULL,
  valuation_time DATETIME(6) NOT NULL,
  source_name VARCHAR(100) NOT NULL,
  UNIQUE (collateral_id, valuation_time),
  CHECK (market_value >= 0 AND adjusted_value >= 0 AND adjusted_value <= market_value),
  CHECK (haircut_pct >= 0 AND haircut_pct < 100),
  FOREIGN KEY (collateral_id) REFERENCES collateral(collateral_id)
) ENGINE=InnoDB;

-- 22. mark_to_market_snapshot
-- Coverage is collateral / loan value * 100; NULL when loan value is zero. Immutable snapshots.
CREATE TABLE mark_to_market_snapshot (
  snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  price_id BIGINT NOT NULL,
  outstanding_quantity BIGINT NOT NULL,
  market_price DECIMAL(20,6) NOT NULL,
  loan_value DECIMAL(20,2) NOT NULL,
  required_collateral DECIMAL(20,2) NOT NULL,
  current_adjusted_collateral DECIMAL(20,2) NOT NULL,
  coverage_pct DECIMAL(12,6),
  snapshot_time DATETIME(6) NOT NULL,
  UNIQUE (contract_id, snapshot_time),
  CHECK (outstanding_quantity >= 0 AND market_price > 0 AND loan_value >= 0),
  CHECK (required_collateral >= 0 AND current_adjusted_collateral >= 0),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (price_id) REFERENCES security_price(price_id)
) ENGINE=InnoDB;

-- 23. margin_call
CREATE TABLE margin_call (
  margin_call_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  margin_call_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_id BIGINT NOT NULL,
  snapshot_id BIGINT NOT NULL,
  required_collateral DECIMAL(20,2) NOT NULL,
  current_collateral DECIMAL(20,2) NOT NULL,
  shortfall_amount DECIMAL(20,2) GENERATED ALWAYS AS (required_collateral-current_collateral) STORED,
  status ENUM('OPEN','PARTIALLY_SATISFIED','SATISFIED','FAILED','BREACHED','CANCELLED') NOT NULL DEFAULT 'OPEN',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  due_at DATETIME(6) NOT NULL,
  resolved_at DATETIME(6),
  CHECK (required_collateral > current_collateral AND current_collateral >= 0),
  CHECK (due_at > created_at),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (snapshot_id) REFERENCES mark_to_market_snapshot(snapshot_id)
) ENGINE=InnoDB;

-- 24. recall_request
CREATE TABLE recall_request (
  recall_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  recall_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_id BIGINT NOT NULL,
  requested_quantity BIGINT NOT NULL,
  returned_quantity BIGINT NOT NULL DEFAULT 0,
  requested_by BIGINT NOT NULL,
  requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  due_at DATETIME(6) NOT NULL,
  recall_reason VARCHAR(500) NOT NULL,
  recall_status ENUM('OPEN','PROCESSING','PARTIALLY_RETURNED','COMPLETED','OVERDUE','DEFAULTED','CANCELLED') NOT NULL DEFAULT 'OPEN',
  CHECK (requested_quantity > 0 AND returned_quantity BETWEEN 0 AND requested_quantity),
  CHECK (due_at > requested_at),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (requested_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 25. settlement_instruction
-- Each instruction settles all-or-none. Split partial fills into separate instructions; never overwrite completed transfers.
CREATE TABLE settlement_instruction (
  instruction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  instruction_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_id BIGINT NOT NULL,
  collateral_id BIGINT,
  recall_id BIGINT,
  margin_call_id BIGINT,
  instruction_type ENUM('SECURITY_DELIVERY','COLLATERAL_TRANSFER','SECURITY_RETURN','COLLATERAL_RELEASE') NOT NULL,
  from_organization_id BIGINT NOT NULL,
  to_organization_id BIGINT NOT NULL,
  from_account VARCHAR(255) NOT NULL,
  to_account VARCHAR(255) NOT NULL,
  security_id BIGINT,
  quantity DECIMAL(24,6),
  amount DECIMAL(20,2),
  currency CHAR(3) NOT NULL,
  status ENUM('PENDING','PROCESSING','SETTLED','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  settlement_date DATE NOT NULL,
  settled_at DATETIME(6),
  external_confirmation_reference VARCHAR(150) UNIQUE,
  failure_reason VARCHAR(500),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (from_organization_id <> to_organization_id),
  CHECK ((quantity IS NOT NULL AND quantity > 0 AND security_id IS NOT NULL AND amount IS NULL)
   OR (amount IS NOT NULL AND amount > 0 AND quantity IS NULL AND security_id IS NULL)),
  CHECK ((instruction_type IN ('SECURITY_DELIVERY','SECURITY_RETURN') AND collateral_id IS NULL AND quantity IS NOT NULL)
   OR (instruction_type IN ('COLLATERAL_TRANSFER','COLLATERAL_RELEASE') AND collateral_id IS NOT NULL)),
  CHECK (status <> 'SETTLED' OR settled_at IS NOT NULL),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (collateral_id) REFERENCES collateral(collateral_id),
  FOREIGN KEY (recall_id) REFERENCES recall_request(recall_id),
  FOREIGN KEY (margin_call_id) REFERENCES margin_call(margin_call_id),
  FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id)
) ENGINE=InnoDB;

-- 26. inventory_movement
-- Loan-security movements only. Collateral movements are recorded by settled instructions.
CREATE TABLE inventory_movement (
  movement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  settlement_instruction_id BIGINT NOT NULL UNIQUE,
  security_id BIGINT NOT NULL,
  from_organization_id BIGINT NOT NULL,
  to_organization_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL,
  movement_type ENUM('LOAN_DELIVERY','RETURN','RECALL_RETURN') NOT NULL,
  movement_timestamp DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (quantity > 0 AND from_organization_id <> to_organization_id),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (settlement_instruction_id) REFERENCES settlement_instruction(instruction_id),
  FOREIGN KEY (security_id) REFERENCES security_master(security_id),
  FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- 27. margin_call_transaction_log
CREATE TABLE margin_call_transaction_log (
  log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  margin_call_id BIGINT NOT NULL,
  settlement_instruction_id BIGINT,
  activity_type ENUM('ISSUED','TOP_UP_CONFIRMED','SATISFIED','BREACHED','FAILED','CANCELLED','NOTE') NOT NULL,
  amount DECIMAL(20,2),
  source_event_id CHAR(36),
  notes VARCHAR(500),
  activity_timestamp DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (amount IS NULL OR amount >= 0),
  UNIQUE (margin_call_id, settlement_instruction_id, activity_type),
  FOREIGN KEY (margin_call_id) REFERENCES margin_call(margin_call_id),
  FOREIGN KEY (settlement_instruction_id) REFERENCES settlement_instruction(instruction_id)
) ENGINE=InnoDB;

-- 28. borrower_trade_activity
-- Borrower-local simulation activity; does not change the borrowing obligation. Currency derives from contract.
CREATE TABLE borrower_trade_activity (
  trade_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  trade_reference VARCHAR(80) NOT NULL UNIQUE,
  contract_id BIGINT NOT NULL,
  trade_type ENUM('SELL_SHORT','BUY_TO_COVER') NOT NULL,
  quantity BIGINT NOT NULL,
  price DECIMAL(20,6) NOT NULL,
  realized_profit_loss DECIMAL(20,2),
  trade_time DATETIME(6) NOT NULL,
  created_by BIGINT NOT NULL,
  CHECK (quantity > 0 AND price > 0),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  FOREIGN KEY (created_by) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 29. rebate_calculation
-- One daily accrual using agreed daily balance convention. Signed rebate supports negative rates.
CREATE TABLE rebate_calculation (
  rebate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  calculation_date DATE NOT NULL,
  eligible_cash_balance DECIMAL(20,2) NOT NULL,
  annual_rebate_pct DECIMAL(10,6) NOT NULL,
  day_count_denominator SMALLINT NOT NULL,
  calculated_amount DECIMAL(20,6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (contract_id, calculation_date),
  CHECK (eligible_cash_balance >= 0),
  CHECK (day_count_denominator IN (360,365)),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) ENGINE=InnoDB;

-- 30. lending_fee_accrual
-- One daily accrual; agreed fee snapshots prevent historical repricing.
CREATE TABLE lending_fee_accrual (
  accrual_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  calculation_date DATE NOT NULL,
  loan_value DECIMAL(20,2) NOT NULL,
  annual_fee_pct DECIMAL(10,6) NOT NULL,
  day_count_denominator SMALLINT NOT NULL,
  calculated_amount DECIMAL(20,6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE (contract_id, calculation_date),
  CHECK (loan_value >= 0),
  CHECK (day_count_denominator IN (360,365)),
  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) ENGINE=InnoDB;

-- 31. rebate_payment_history
-- One row per payment attempt/allocation. Backend prevents overpayment; payer direction handles signed rebate.
CREATE TABLE rebate_payment_history (
  payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_reference VARCHAR(80) NOT NULL UNIQUE,
  rebate_id BIGINT NOT NULL,
  from_organization_id BIGINT NOT NULL,
  to_organization_id BIGINT NOT NULL,
  payment_amount DECIMAL(20,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  status ENUM('PENDING','PAID','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  paid_at DATETIME(6),
  external_reference VARCHAR(150) UNIQUE,
  failure_reason VARCHAR(500),
  CHECK (payment_amount > 0 AND from_organization_id <> to_organization_id),
  CHECK (status <> 'PAID' OR paid_at IS NOT NULL),
  FOREIGN KEY (rebate_id) REFERENCES rebate_calculation(rebate_id),
  FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- 32. lending_fee_payment
-- One row per payment attempt/allocation. Backend prevents overpayment; payer direction handles signed rebate.
CREATE TABLE lending_fee_payment (
  payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_reference VARCHAR(80) NOT NULL UNIQUE,
  accrual_id BIGINT NOT NULL,
  from_organization_id BIGINT NOT NULL,
  to_organization_id BIGINT NOT NULL,
  payment_amount DECIMAL(20,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  status ENUM('PENDING','PAID','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  paid_at DATETIME(6),
  external_reference VARCHAR(150) UNIQUE,
  failure_reason VARCHAR(500),
  CHECK (payment_amount > 0 AND from_organization_id <> to_organization_id),
  CHECK (status <> 'PAID' OR paid_at IS NOT NULL),
  FOREIGN KEY (accrual_id) REFERENCES lending_fee_accrual(accrual_id),
  FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- 33. message_log
-- Business audit log; not a replacement for QuickFIX/J session/message persistence.
CREATE TABLE message_log (
  message_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id CHAR(36) NOT NULL,
  service_name VARCHAR(80) NOT NULL,
  direction ENUM('INBOUND','OUTBOUND') NOT NULL,
  correlation_id VARCHAR(80) NOT NULL,
  causation_event_id CHAR(36),
  sender_org_id BIGINT NOT NULL,
  receiver_org_id BIGINT NOT NULL,
  application_message_type VARCHAR(100) NOT NULL,
  fix_message_type VARCHAR(8),
  fix_version VARCHAR(20),
  fix_session_id VARCHAR(150),
  fix_sequence_number BIGINT,
  raw_fix LONGTEXT,
  payload JSON NOT NULL,
  schema_version INT NOT NULL DEFAULT 1,
  kafka_topic VARCHAR(255),
  kafka_partition INT,
  kafka_offset BIGINT,
  processing_status ENUM('PENDING','PUBLISHED','PROCESSED','REJECTED','FAILED') NOT NULL DEFAULT 'PENDING',
  error_message TEXT,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at DATETIME(6),
  UNIQUE (service_name, direction, event_id),
  FOREIGN KEY (sender_org_id) REFERENCES organization(organization_id),
  FOREIGN KEY (receiver_org_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- 34. outbox_event
-- Write with business changes in one local transaction. Relay may publish duplicates; consumers deduplicate.
CREATE TABLE outbox_event (
  outbox_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id CHAR(36) NOT NULL UNIQUE,
  service_name VARCHAR(80) NOT NULL,
  aggregate_type VARCHAR(80) NOT NULL,
  aggregate_reference VARCHAR(80) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  correlation_id VARCHAR(80) NOT NULL,
  sender_org_id BIGINT NOT NULL,
  receiver_org_id BIGINT NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  schema_version INT NOT NULL DEFAULT 1,
  kafka_topic VARCHAR(255) NOT NULL,
  partition_key VARCHAR(150) NOT NULL,
  payload JSON NOT NULL,
  status ENUM('PENDING','PUBLISHING','PUBLISHED','FAILED') NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  locked_by VARCHAR(150),
  locked_until DATETIME(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  published_at DATETIME(6),
  last_error TEXT,
  CHECK (attempt_count >= 0 AND aggregate_version >= 0),
  FOREIGN KEY (sender_org_id) REFERENCES organization(organization_id),
  FOREIGN KEY (receiver_org_id) REFERENCES organization(organization_id)
) ENGINE=InnoDB;

-- 35. inbox_message
-- Commit processed inbox record and business changes atomically; acknowledge Kafka only after commit.
CREATE TABLE inbox_message (
  inbox_id BIGINT AUTO_INCREMENT PRIMARY KEY,
  consumer_name VARCHAR(150) NOT NULL,
  event_id CHAR(36) NOT NULL,
  correlation_id VARCHAR(80) NOT NULL,
  aggregate_reference VARCHAR(80) NOT NULL,
  aggregate_version BIGINT NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  payload JSON NOT NULL,
  kafka_topic VARCHAR(255) NOT NULL,
  kafka_partition INT NOT NULL,
  kafka_offset BIGINT NOT NULL,
  status ENUM('RECEIVED','PROCESSED','REJECTED') NOT NULL DEFAULT 'RECEIVED',
  received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at DATETIME(6),
  error_message TEXT,
  UNIQUE (consumer_name, event_id),
  CHECK (kafka_partition >= 0 AND kafka_offset >= 0)
) ENGINE=InnoDB;

CREATE INDEX idx_locate_status_expiry ON locate_request(status, expiry_time);
CREATE INDEX idx_request_status_time ON loan_request(status, requested_at);
CREATE INDEX idx_contract_status ON loan_contract(contract_status);
CREATE INDEX idx_reservation_expiry ON inventory_reservation(status, expires_at);
CREATE INDEX idx_contract_history_time ON contract_status_history(contract_id, changed_at);
CREATE INDEX idx_margin_status_due ON margin_call(status, due_at);
CREATE INDEX idx_recall_status_due ON recall_request(recall_status, due_at);
CREATE INDEX idx_settlement_status_date ON settlement_instruction(status, settlement_date);
CREATE INDEX idx_movement_contract_time ON inventory_movement(contract_id, movement_timestamp);
CREATE INDEX idx_message_correlation ON message_log(correlation_id, created_at);
CREATE INDEX idx_outbox_publish ON outbox_event(service_name, status, next_attempt_at);
CREATE INDEX idx_inbox_correlation ON inbox_message(correlation_id);
