# Securities lending — four services

Fresh creation scripts define the baseline; additive scripts under `migrations/` are applied separately. Service implementation status is tracked in `docs/PROGRESS.md`.

## Run
Use a migration administrator on empty databases. Run `00_all_databases.sql` OR the four individual scripts 01–04, not both. MySQL 8.0.16+ is required for enforced CHECK constraints. Replace example passwords/hosts in `05_permissions_template.sql` before use. Runtime credentials have access only to their own database. One MySQL server may host all four databases.

For existing Borrower databases, apply `migrations/015_borrower_cash_account_and_transaction.sql` to add organization/currency cash accounts and posted cash transaction history. The Borrower service must update the account balance and insert its transaction in the same local transaction. Use `transaction_reference` as a stable unique reference; related lifecycle records are stored as type/reference values, not foreign keys.

For an existing Custodian database whose local foreign keys are missing, first check for orphan rows, then apply `migrations/016_custodian_local_foreign_keys.sql` once. It restores the local constraints declared in `02_custodian_db.sql`; it does not add cross-service foreign keys.

## Borrower cash accounts

`POST /api/cash-accounts` opens a zero-balance account for the authenticated borrower's organization and a currency, for example `{"currency":"USD"}`. Use `GET /api/cash-accounts` or `GET /api/cash-accounts/{currency}` to read balances, and `GET /api/cash-accounts/{currency}/transactions` to page through posted movements.

Borrowers can self-report an external deposit or withdrawal with `POST /api/cash-accounts/{currency}/transactions`. Include an `Idempotency-Key` header and a body such as `{"transactionType":"CREDIT","amount":2500.00,"externalReference":"BANK-001","description":"Opening cash deposit","transactionTime":"2026-11-18T05:30:00Z"}`. This immediately changes the recorded balance; it is a borrower-entered record, not a bank-verified transfer. The authenticated user is saved as the recorder. Reusing the idempotency key with matching details does not post the movement twice; conflicting reuse is rejected. Debits that would make the balance negative are rejected.

System-recorded movements are posted from the Borrower service's existing lifecycle events: executed short-sale proceeds are credited, buy-to-cover execution cost is debited, confirmed collateral settlement transfers are debited and releases credited, and fee/rebate payments affect cash only when marked paid. Trade execution amounts are currently recorded when the execution report is processed; separate market cash-settlement confirmation is not modeled. Trade `realized_profit_loss` is not added separately, and this feature does not calculate P&L or track reserved-versus-available cash. Existing balances are not backfilled by migration; an account starts at zero until cash movements are recorded.

For existing Borrower databases, `migrations/009_borrower_inventory.sql` adds the inventory projection and seeds it from contracts with outstanding delivered securities. The Borrower service then keeps each position synchronized from Custodian settlement events.

For existing Borrower databases, apply `migrations/010_borrower_order.sql` and then `migrations/011_borrower_trade_activity.sql` to add order and trade capture. Trade rows reference their order; execution reports must come from a trusted source. For databases that already have a `borrower_trade_activity` table without `order_reference`, apply `migrations/012_borrower_trade_order_link.sql` instead of 011.

For existing Custodian databases, apply `migrations/013_msla_annual_rebate.sql` to add the negotiated annual rebate rate to MSLA agreements. The Custodian enters the rate as a percentage (for example, `3` means 3% per year); approved contracts snapshot it. Each completed IST accrual day uses actual settled cash collateral held, ACT/365, and daily HALF_UP rounding to cents. Cash receipt and release settlement dates count as full days. The rate applies only within the MSLA effective/expiry dates and through contract close. Custodian can run or query accruals with `POST /api/contracts/{ref}/rebates/accruals?from=YYYY-MM-DD&to=YYYY-MM-DD` and `GET /api/contracts/{ref}/rebates`; the scheduled accrual runs at 00:10 Asia/Kolkata by default. Borrower receives posted accruals in its contract projection. Rebate payout/payment processing is not implemented; do not treat accruals as payments.

For existing Custodian databases, apply `migrations/014_rebate_payment_history.sql` if `rebate_payment_history` is absent. Payouts are manually initiated by an authorized Custodian operator, one current posted daily accrual per payout; the lender pays the Borrower. Create with `POST /api/contracts/{ref}/rebates/payments` using `{"paymentReference":"RB-001","calculationResultReference":"<current-result-uuid>"}`. The server selects the payable amount and rejects stale results or duplicate active/completed payments. After an external transfer, record it with `POST /api/contracts/{ref}/rebates/payments/{paymentReference}/confirm` and `{"externalConfirmationReference":"BANK-001"}`; use `/fail` with `{"reason":"..."}` if it does not settle. A failed payout can be retried using a new payment reference. `GET /api/contracts/{ref}/rebates/payments` lists history. Payout confirmation is an operator record of external settlement, not an integrated bank transfer. Contract closure is blocked until each positive current rebate accrual is paid. Accrual corrections are blocked while a payout for that accrual is pending or paid.

Example review scenario: with INR 105,000 settled cash and a 3% annual MSLA rebate, a completed ACT/365 day accrues INR 8.63. Run accrual for that date, inspect `latestResultReference`, create a payout for it, then confirm using the external bank reference. Closure while that payout is PENDING should return a conflict; after it is PAID, the rebate gate passes (other closure checks still apply).

## Ownership and simplifications
See TABLE_OWNERSHIP.md for every table. Security master/prices live only in Security & Market Data. Custodian owns organization registry, agreements, credit, lender inventory, contract/settlement obligations and payment states. Borrower owns submitted requests, the decision-fed `loan_contract_view`, orders, trade activity, and its cash account and posted cash transactions. Calculation owns versioned financial runs/results. `borrower_identity_link` records one-time link-code consumption. A request appears in both Borrower and Custodian because submission intent and processing decisions have different owners; shared request references correlate them.

External references replace remote foreign keys: `security_isin`, `msla_reference`, `transaction_reference`, organization references and calculation result UUIDs. Local FKs are retained. Custodian owns MSLA and therefore retains its local MSLA FKs. Borrower fetches current security/MSLA/contracts/obligations over REST. Historical terms required for decisions are snapshots, not live remote joins. The UI shows queued/sent separately from approved and handles unavailable reference services explicitly.

## JSON kept and where it comes from
- `msla_agreement.default_rules`: constructed/validated/processed by Custodian backend. Not replaced by typed rule columns.
- `eligible_securities`: Custodian stores allowed asset types, e.g. {"allowed_types":["EQUITY","ETF"]}. Selected ISIN is validated against Security Master asset type, not an MSLA security-ID mapping table.
- `loan_contract.collateral_terms`: built at approval from MSLA conditions and effective haircut rule. Current policy CASH and 0% haircut, enforced by backend. Coverage remains a separate authoritative column. Other supported collateral ENUM values and multi-asset collateral constraints remain for future use.
- `msla_agreement.agreed_annual_rebate_pct`: Custodian-entered negotiated annual rebate rate. Each approved loan snapshots this rate; daily accrual uses only settled cash collateral actually held.
- `calculation_run.inputs`: immutable full input snapshot: authoritative contract/MSLA versions, balances, quantities, dates, rates, thresholds, day-count, rounding and any payment adjustments. `price_snapshots` holds exact selected price reference, ISIN, currency, source, value and timestamp. `rule_parameters`, `rule_version`, and `implementation_digest` pin the calculation implementation without taking ownership of agreement terms.
- `calculation_result.details`: validated, schema-versioned detailed results. Common amounts have typed columns; different result types keep their specific fields in JSON. Rows distinguish MTM, individual collateral valuations, daily lending-fee accruals, rebates, margin/release amounts and payment-due results. `item_key` identifies one result item inside a run; results are immutable.
- `loan_contract.accepted_valuation_snapshot`: accepted result details, result reference and input versions. `margin_call.calculation_snapshot` and payment `calculation_snapshot` preserve the evidence used to create obligations. These are decision evidence, not separate calculation engines.
- `loan_contract_view.contract_snapshot`: Borrower's local, read-only snapshot copied from a validated Custodian decision event; it does not own or modify the Custodian contract.
- Messaging payloads remain JSON. Raw FIX is preserved separately or inside the documented envelope. A message-type label alone is not FIX.

## Financial scope
Valuation & Accrual performs valuation, coverage, margin/release amounts, fees, rebates and payment-due calculations. Quantity checks, permissions, eligibility, credit/inventory reservation and workflow decisions remain Borrower/Custodian responsibilities. Custodian applies results and owns payments. Borrower previews never establish obligations. Recall requests can exist without a collateral shortfall; recall deadlines are workflow policy.

Current executable workflow must enforce same-currency CASH collateral and 0% haircut. Reject currency mismatch until FX inputs/conversion are designed. Zero loan value produces NULL coverage, not division by zero. Lending fees and rebates use their separately approved day-count, rounding, and accrual bases. Negative rebates are rejected; rebates are accruals, not payments.

## Fewer tables without dropping audit or reliability
The four previous valuation/accrual table types are consolidated into calculation_result with result_type, typed common values and versioned detail JSON; input and price snapshots are consolidated into calculation_run. Lending-fee and rebate payment records link stable result and obligation references, plus a local contract FK. Rebate calculation results and accrual events use the existing calculation and messaging records. Corrections create new run/result references with supersedes links and adjustment logic, not overwrites; paid or pending rebate obligations cannot be corrected. Accepted snapshots must include approved obligation totals, currency, period, result reference and input versions.

Calendars are versioned service configuration, not additional tables: calendar code, timezone, explicit business-day/holiday data and cutoff behavior. Agreement identifies the calendar; contract records the calendar version used. Store applied scheduling evidence in contract collateral_terms metadata or status-history reason as appropriate. T+1 scheduler uses the configured next business day, never a 24-hour delay. Missing calendar coverage must fail explicitly. Actual settlement completion requires confirmations.

All four services retain outbox_event, inbox_message and message_log. A dedicated sequence table is unnecessary: update the persistent owner row `version` under lock with business changes and outbox insertion in one transaction. The event aggregate must be that owner row (not an invented stream with no stored version); event IDs distinguish multiple event types at one state version. Consumers deduplicate by consumer/event ID; do not suppress distinct events solely because they share a state version. Never derive versions from outbox rows that may be purged. Immutable price/result rows use their own stable references as aggregates. Acknowledge consumption after commit. Use leases, retries, dead-letter topics and controlled replay.

## Identity and FIX
Assume a shared JWT signing issuer. `users`/`roles`/`user_roles` are service-local authorization records, not replicated passwords. Borrower `organization_reference` is externally validated through authenticated claims and Custodian registry. Market writes require a trusted market operator role claim. Every REST/event operation enforces its service's authorization scope. QuickFIX/J adapters are a future integration; none are implemented in this checkout.

borrower_order is the AF existing-order source. ExecutionReport (8) responses are audited in message_log/outbox, so no separate order-status-report table is needed. f reports instrument status through an authorized Security & Market Data owner update; it is not loan lifecycle status. BB/BA and AE/AK remain subject to the agreed FIX dictionary/field mapping. Kafka transport does not automatically implement FIX session logon, heartbeat or recovery.

## Implementation order
1. Confirm ownership (TABLE_OWNERSHIP.md).
2. Create four databases with isolated credentials.
3. Implement external-reference validation and keep local FKs.
4. Implement REST/Kafka contracts (COMMUNICATION.md).
5. Implement request response updates and required event consumers; run integration tests. Add no new physical tables without exceeding the agreed 50-table ceiling.

## Required backend checks
MSLA validity and borrower/mandate relationship; eligible asset type; available credit; atomic inventory reservation; effective non-overlapping terms; same-contract settlement links; permitted statuses; one active margin/recall according to policy; no double settlement, payment or release; authorization; immutable history. Revalidate current versions after REST calculation before committing approval; do not hold database locks across network calls. Invalid/stale calculations are rejected and recalculated. Service outages must return pending/error, not fabricate financial results.

Schema constraints support these rules but do not implement the workflow. Runtime implementation and tests are still required.
