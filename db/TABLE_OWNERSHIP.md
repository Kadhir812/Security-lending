# 49-table ownership map

| Database | Table | Ownership |
|---|---|---|
| security_market_db | `security_master` | AUTHORITATIVE LOCAL RECORD |
| security_market_db | `message_log` | TECHNICAL |
| security_market_db | `outbox_event` | TECHNICAL |
| security_market_db | `inbox_message` | TECHNICAL |
| security_market_db | `security_price` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `organization` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `roles` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `message_log` | TECHNICAL |
| custodian_db | `outbox_event` | TECHNICAL |
| custodian_db | `inbox_message` | TECHNICAL |
| custodian_db | `users` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `lender_custodian_mandate` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `lender_inventory` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `user_roles` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `msla_agreement` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `counterparty_credit_limit` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `collateral_haircut` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `lending_fee` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `locate_request` | RECEIVED INTENT + AUTHORITATIVE DECISION |
| custodian_db | `credit_limit_history` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `loan_request` | RECEIVED INTENT + AUTHORITATIVE DECISION |
| custodian_db | `loan_contract` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `inventory_reservation` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `contract_status_history` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `collateral` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `recall_request` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `margin_call` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `rebate_payment_history` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `lending_fee_payment` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `settlement_instruction` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `inventory_movement` | AUTHORITATIVE LOCAL RECORD |
| custodian_db | `margin_call_transaction_log` | AUTHORITATIVE LOCAL RECORD |
| borrower_db | `roles` | AUTHORITATIVE LOCAL RECORD |
| borrower_db | `message_log` | TECHNICAL |
| borrower_db | `outbox_event` | TECHNICAL |
| borrower_db | `inbox_message` | TECHNICAL |
| borrower_db | `users` | AUTHORITATIVE LOCAL RECORD |
| borrower_db | `borrower_inventory` | BORROWER HOLDING; SETTLED QUANTITY UPDATED FROM CUSTODIAN EVENTS |
| borrower_db | `user_roles` | AUTHORITATIVE LOCAL RECORD |
| borrower_db | `locate_request` | SUBMITTED INTENT + COPIED DECISION |
| borrower_db | `borrower_trade_activity` | AUTHORITATIVE LOCAL RECORD |
| borrower_db | `borrower_order` | AUTHORITATIVE LOCAL RECORD |
| borrower_db | `loan_request` | SUBMITTED INTENT + COPIED DECISION |
| borrower_db | `loan_contract_view` | READ-ONLY CUSTODIAN CONTRACT SNAPSHOT |
| valuation_accrual_db | `calculation_run` | AUTHORITATIVE LOCAL RECORD |
| valuation_accrual_db | `message_log` | TECHNICAL |
| valuation_accrual_db | `outbox_event` | TECHNICAL |
| valuation_accrual_db | `inbox_message` | TECHNICAL |
| valuation_accrual_db | `calculation_result` | AUTHORITATIVE LOCAL RECORD |

No standalone reference/contract projection tables. Borrower reads current master, MSLA, contract and obligation details over authenticated REST. Copied response fields in request records remain authoritative at Custodian. No service edits another service database.
