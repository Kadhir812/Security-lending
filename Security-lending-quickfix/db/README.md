# Securities lending — four services, 49 physical tables

This replaces the proposed 72-table design. Fresh creation scripts define the baseline; additive scripts under `migrations/` are applied separately. Service implementation status is tracked in `docs/PROGRESS.md`.

| Database | Tables (includes three messaging tables each) |
|---|---:|
| security_market_db | 5 |
| custodian_db | 27 |
| borrower_db | 12 |
| valuation_accrual_db | 5 |
| Total | 49 |

## Run
Use a migration administrator on empty databases. Run `00_all_databases.sql` OR the four individual scripts 01–04, not both. MySQL 8.0.16+ is required for enforced CHECK constraints. Replace example passwords/hosts in `05_permissions_template.sql` before use. Runtime credentials have access only to their own database. One MySQL server may host all four databases.

For existing databases, apply each additive migration in `migrations/` once and
in numerical order using a migration administrator. In particular,
`migrations/005_demo_password_auth.sql` adds the columns and role seeds needed
for Borrower/Custodian demo sign-up; `migrations/006_remove_administrator_role.sql`
removes old Administrator assignments and role rows. These migrations are not
needed for newly created databases using the current fresh-schema scripts.

## Ownership and simplifications
See TABLE_OWNERSHIP.md for every table. Security master/prices live only in Security & Market Data. Custodian owns organization registry, agreements, credit, lender inventory, contract/settlement obligations and payment states. Borrower owns submitted requests, the decision-fed `loan_contract_view`, orders and borrower activity. Calculation owns versioned financial runs/results. The contract view is the sole added projection table and keeps the schema under the 50-table limit. A request appears in both Borrower and Custodian because submission intent and processing decisions have different owners; shared request references correlate them.

External references replace remote foreign keys: `security_isin`, `msla_reference`, `transaction_reference`, organization references and calculation result UUIDs. Local FKs are retained. Custodian owns MSLA and therefore retains its local MSLA FKs. Borrower fetches current security/MSLA/contracts/obligations over REST. Historical terms required for decisions are snapshots, not live remote joins. The UI shows queued/sent separately from approved and handles unavailable reference services explicitly.

## JSON kept and where it comes from
- `msla_agreement.default_rules`: constructed/validated/processed by Custodian backend. Not replaced by typed rule columns.
- `eligible_securities`: Custodian stores allowed asset types, e.g. {"allowed_types":["EQUITY","ETF"]}. Selected ISIN is validated against Security Master asset type, not an MSLA security-ID mapping table.
- `loan_contract.collateral_terms`: built at approval from MSLA conditions and effective haircut rule. Current policy CASH and 0% haircut, enforced by backend. Coverage remains a separate authoritative column. Other supported collateral ENUM values and multi-asset collateral constraints remain for future use.
- `calculation_run.inputs`: immutable full input snapshot: authoritative contract/MSLA versions, balances, quantities, dates, rates, thresholds, day-count, rounding and any payment adjustments. `price_snapshots` holds exact selected price reference, ISIN, currency, source, value and timestamp. `rule_parameters`, `rule_version`, and `implementation_digest` pin the calculation implementation without taking ownership of agreement terms.
- `calculation_result.details`: validated, schema-versioned detailed results. Common amounts have typed columns; different result types keep their specific fields in JSON. Rows distinguish MTM, individual collateral valuations, daily lending-fee accruals, rebates, margin/release amounts and payment-due results. `item_key` identifies one result item inside a run; results are immutable.
- `loan_contract.accepted_valuation_snapshot`: accepted result details, result reference and input versions. `margin_call.calculation_snapshot` and payment `calculation_snapshot` preserve the evidence used to create obligations. These are decision evidence, not separate calculation engines.
- `loan_contract_view.contract_snapshot`: Borrower's local, read-only snapshot copied from a validated Custodian decision event; it does not own or modify the Custodian contract.
- Messaging payloads remain JSON. Raw FIX is preserved separately or inside the documented envelope. A message-type label alone is not FIX.

## Financial scope
Valuation & Accrual performs valuation, coverage, margin/release amounts, fees, rebates and payment-due calculations. Quantity checks, permissions, eligibility, credit/inventory reservation and workflow decisions remain Borrower/Custodian responsibilities. Custodian applies results and owns payments. Borrower previews never establish obligations. Recall requests can exist without a collateral shortfall; recall deadlines are workflow policy.

Current executable workflow must enforce same-currency CASH collateral and 0% haircut. Reject currency mismatch until FX inputs/conversion are designed. Zero loan value produces NULL coverage, not division by zero. Agree day-count, rounding, thresholds and fee/rebate bases before implementation. Negative rebates require correct payment direction with positive payment amounts. Do not treat accruals as payments.

## Fewer tables without dropping audit or reliability
The four previous valuation/accrual table types are consolidated into calculation_result with result_type, typed common values and versioned detail JSON; input and price snapshots are consolidated into calculation_run. Fee/rebate payment records link stable result and obligation references, plus a local contract FK. Backend locks the contract/obligation scope to prevent overpayment, duplicated obligations and paying superseded results. Corrections create new run/result references with supersedes links and adjustment logic, not overwrites. Accepted snapshots must include approved obligation totals, currency, period, result reference and input versions.

Calendars are versioned service configuration, not additional tables: calendar code, timezone, explicit business-day/holiday data and cutoff behavior. Agreement identifies the calendar; contract records the calendar version used. Store applied scheduling evidence in contract collateral_terms metadata or status-history reason as appropriate. T+1 scheduler uses the configured next business day, never a 24-hour delay. Missing calendar coverage must fail explicitly. Actual settlement completion requires confirmations.

All four services retain outbox_event, inbox_message and message_log. A dedicated sequence table is unnecessary: update the persistent owner row `version` under lock with business changes and outbox insertion in one transaction. The event aggregate must be that owner row (not an invented stream with no stored version); event IDs distinguish multiple event types at one state version. Consumers deduplicate by consumer/event ID; do not suppress distinct events solely because they share a state version. Never derive versions from outbox rows that may be purged. Immutable price/result rows use their own stable references as aggregates. Acknowledge consumption after commit. Use leases, retries, dead-letter topics and controlled replay.

## Identity and FIX
users/roles/user_roles are service-local authorization records. Optional
Borrower/Custodian demo login stores only BCrypt password hashes in each
service's own `users` table; it is not a production identity provider.
Borrower organization_reference is externally validated through authenticated
claims and Custodian registry. Market/Calculation use service authentication.
Every REST/event operation enforces organization scope. QuickFIX/J adapters
stay inside Borrower and Custodian.

borrower_order is the AF existing-order source. ExecutionReport (8) responses are audited in message_log/outbox, so no separate order-status-report table is needed. f reports instrument status through an authorized Security & Market Data owner update; it is not loan lifecycle status. BB/BA and AE/AK remain subject to the agreed FIX dictionary/field mapping. Kafka transport does not automatically implement FIX session logon, heartbeat or recovery.

## Implementation order
1. Confirm ownership (TABLE_OWNERSHIP.md).
2. Create four databases with isolated credentials.
3. Implement external-reference validation and keep local FKs.
4. Implement REST/Kafka contracts (COMMUNICATION.md).
5. Implement request response updates and required event consumers; run integration tests. Add no new physical tables without staying under the agreed 50-table total.

## Required backend checks
MSLA validity and borrower/mandate relationship; eligible asset type; available credit; atomic inventory reservation; effective non-overlapping terms; same-contract settlement links; permitted statuses; one active margin/recall according to policy; no double settlement, payment or release; authorization; immutable history. Revalidate current versions after REST calculation before committing approval; do not hold database locks across network calls. Invalid/stale calculations are rejected and recalculated. Service outages must return pending/error, not fabricate financial results.

Schema constraints support these rules but do not implement the workflow. Runtime implementation and tests are still required.
