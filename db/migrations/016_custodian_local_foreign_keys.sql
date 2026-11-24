-- Apply once to an existing Custodian database after verifying there are no
-- orphan rows in the referenced columns. This restores the local relationships
-- defined by 02_custodian_db.sql; cross-service references remain unconstrained.
USE custodian_db;

ALTER TABLE lender_custodian_mandate
  ADD CONSTRAINT fk_lender_custodian_mandate_lender_org
    FOREIGN KEY (lender_org_id) REFERENCES organization(organization_id),
  ADD CONSTRAINT fk_lender_custodian_mandate_custodian_org
    FOREIGN KEY (custodian_org_id) REFERENCES organization(organization_id);

ALTER TABLE borrower_identity_link
  ADD CONSTRAINT fk_borrower_identity_link_org_reference
    FOREIGN KEY (organization_reference) REFERENCES organization(organization_reference),
  ADD CONSTRAINT fk_borrower_identity_link_custodian
    FOREIGN KEY (consumed_by_custodian_id) REFERENCES organization(organization_id),
  ADD CONSTRAINT fk_borrower_identity_link_user
    FOREIGN KEY (consumed_by_user_id) REFERENCES users(user_id);

ALTER TABLE msla_agreement
  ADD CONSTRAINT fk_msla_agreement_mandate
    FOREIGN KEY (mandate_id) REFERENCES lender_custodian_mandate(mandate_id),
  ADD CONSTRAINT fk_msla_agreement_borrower_org
    FOREIGN KEY (borrower_org_id) REFERENCES organization(organization_id);

ALTER TABLE collateral_haircut
  ADD CONSTRAINT fk_collateral_haircut_msla
    FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id);

ALTER TABLE lending_fee
  ADD CONSTRAINT fk_lending_fee_msla
    FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id);

ALTER TABLE locate_request
  ADD CONSTRAINT fk_locate_request_msla
    FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id);

ALTER TABLE credit_limit_history
  ADD CONSTRAINT fk_credit_limit_history_credit_limit
    FOREIGN KEY (credit_limit_id) REFERENCES counterparty_credit_limit(credit_limit_id),
  ADD CONSTRAINT fk_credit_limit_history_changed_by
    FOREIGN KEY (changed_by) REFERENCES users(user_id);

ALTER TABLE loan_request
  ADD CONSTRAINT fk_loan_request_msla
    FOREIGN KEY (msla_id) REFERENCES msla_agreement(msla_id),
  ADD CONSTRAINT fk_loan_request_locate
    FOREIGN KEY (locate_id) REFERENCES locate_request(locate_id);

ALTER TABLE loan_contract
  ADD CONSTRAINT fk_loan_contract_loan_request
    FOREIGN KEY (loan_request_id) REFERENCES loan_request(request_id);

ALTER TABLE credit_exposure_history
  ADD CONSTRAINT fk_credit_exposure_history_credit_limit
    FOREIGN KEY (credit_limit_id) REFERENCES counterparty_credit_limit(credit_limit_id),
  ADD CONSTRAINT fk_credit_exposure_history_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id);

ALTER TABLE inventory_reservation
  ADD CONSTRAINT fk_inventory_reservation_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  ADD CONSTRAINT fk_inventory_reservation_inventory
    FOREIGN KEY (inventory_id) REFERENCES lender_inventory(inventory_id);

ALTER TABLE contract_status_history
  ADD CONSTRAINT fk_contract_status_history_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  ADD CONSTRAINT fk_contract_status_history_changed_by
    FOREIGN KEY (changed_by) REFERENCES users(user_id);

ALTER TABLE collateral
  ADD CONSTRAINT fk_collateral_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id);

ALTER TABLE recall_request
  ADD CONSTRAINT fk_recall_request_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  ADD CONSTRAINT fk_recall_request_requested_by
    FOREIGN KEY (requested_by) REFERENCES users(user_id);

ALTER TABLE margin_call
  ADD CONSTRAINT fk_margin_call_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id);

ALTER TABLE rebate_payment_history
  ADD CONSTRAINT fk_rebate_payment_history_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  ADD CONSTRAINT fk_rebate_payment_history_from_org
    FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  ADD CONSTRAINT fk_rebate_payment_history_to_org
    FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id);

ALTER TABLE lending_fee_payment
  ADD CONSTRAINT fk_lending_fee_payment_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  ADD CONSTRAINT fk_lending_fee_payment_from_org
    FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  ADD CONSTRAINT fk_lending_fee_payment_to_org
    FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id);

ALTER TABLE settlement_instruction
  ADD CONSTRAINT fk_settlement_instruction_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  ADD CONSTRAINT fk_settlement_instruction_collateral
    FOREIGN KEY (collateral_id) REFERENCES collateral(collateral_id),
  ADD CONSTRAINT fk_settlement_instruction_recall
    FOREIGN KEY (recall_id) REFERENCES recall_request(recall_id),
  ADD CONSTRAINT fk_settlement_instruction_margin_call
    FOREIGN KEY (margin_call_id) REFERENCES margin_call(margin_call_id),
  ADD CONSTRAINT fk_settlement_instruction_from_org
    FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  ADD CONSTRAINT fk_settlement_instruction_to_org
    FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id);

ALTER TABLE inventory_movement
  ADD CONSTRAINT fk_inventory_movement_contract
    FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id),
  ADD CONSTRAINT fk_inventory_movement_settlement
    FOREIGN KEY (settlement_instruction_id) REFERENCES settlement_instruction(instruction_id),
  ADD CONSTRAINT fk_inventory_movement_from_org
    FOREIGN KEY (from_organization_id) REFERENCES organization(organization_id),
  ADD CONSTRAINT fk_inventory_movement_to_org
    FOREIGN KEY (to_organization_id) REFERENCES organization(organization_id);

ALTER TABLE margin_call_transaction_log
  ADD CONSTRAINT fk_margin_call_transaction_log_margin_call
    FOREIGN KEY (margin_call_id) REFERENCES margin_call(margin_call_id),
  ADD CONSTRAINT fk_margin_call_transaction_log_settlement
    FOREIGN KEY (settlement_instruction_id) REFERENCES settlement_instruction(instruction_id);
