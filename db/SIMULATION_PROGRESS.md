# Lending simulation progress

Date: 2026-11-11

## Setup and fixtures

- Security & Market Data: `http://localhost:8080/actuator/health` -> `UP`
- Custodian: `http://localhost:8081/actuator/health` -> `UP`
- Borrower: `http://localhost:8082/actuator/health` -> `UP`
- Valuation & Accrual: `http://localhost:8083/actuator/health` -> `UP`
- Kafka: `localhost:9092` -> running
- Borrower and Custodian messaging: enabled; Valuation REST-only for this scenario.
- Fixture: Custodian `SIM-CUSTODIAN`; Borrower `SIM-BORROWER-20261110064910`; lender `SIM-LENDER-20261110064910`.
- Active MSLA `SIM-MSLA-20261110064910` (CASH, 105%); security `INE002A01018` (INR); original request `SIM-SCENARIO1-LOAN-20261110` (100).

## Completed results

- Inventory, credit limit, fee, and zero-percent CASH haircut setup succeeded; Borrower submission reached Custodian through `sec-lending-requests`.
- Valuation returned:
  - Loan market value: `10000.00`
  - Required cash collateral: `10500.00`
- Custodian approval returned `200 OK` (`APPROVED`); contract `CTR-9d4da5c0-d9b9-32e9-8006-63922838350d`.
- Borrower response event was delivered through `sec-lending-responses`.
- Borrower request and contract view both verified as `APPROVED`.

## Repairs applied

- Repaired live schema defaults for inventory, credit, message log, and inbox timestamps.
- Aligned local JWT configuration and service-token authentication.
- Created the missing `sec-lending-responses` Kafka topic.
- Fixed idempotent approval republishing and outbox recovery after failed delivery.
- Fixed Borrower inbox persistence to write `received_at` explicitly.

## Live FIX scenario 2 (2026-11-11)

- Prior 2026-11-10 scenario stopped after approval; collateral was not attempted.
- Run: `SIM-LIVE-FIX-20261111-U1-001`, 100 shares, settlement `2026-11-12`.
- U1 published/consumed on `sec-lending-requests`; event `ad069620-9165-4535-9789-5132a8bfcde9`.
- BB inquiry `fdef2e3f-78eb-4c0b-a4fc-498f2d6de1c4`; BA report `bcd1b8a7-ee12-352f-8a81-106d75d762c5`, CASH/INR, 0% haircut, 105% coverage.
- AK approved contract `CTR-90f3b1fa-4312-379a-ba00-bcf4e12895c8`; loan value `10000.00`, required CASH `10500.00`.
- CASH instruction `SIM-LIVE-FIX-20261111-CASH-001` was confirmed; status `POSTED`.
- U2 delivery `SIM-LIVE-FIX-20261111-SECURITY-001`; event `4e77a32c-00ed-338c-9665-37837bf58f7c`.
- U2 outbox is `PUBLISHED` on `sec-lending-responses`; Borrower inbox is `PROCESSED` once and its contract view is `ON_LOAN`, delivered `100`.
- Atomic settlement result: one delivery movement; reservation `CONSUMED`; inventory on-loan/reserved `100/100`.
- Read-only DB verification: new contract outstanding `100`, reservation consumed `100`, remaining `0`; confirmed CASH `10500 INR` (`POSTED`/`SETTLED`); exactly one initial delivery movement.
- MSLA aggregate is used/reserved `10000/10000`. Its reserved `10000` credit and reserved `100` shares belong to earlier approved, unsettled contract `CTR-9d4da5c0-d9b9-32e9-8006-63922838350d`; the new contract consumed its reservation and used `10000`.
- Pre-fix settlement left the new contract's `reserved_credit_amount=10000` despite transferring aggregate credit to used. Fixed the settlement transition and added reconciliation on an authorized, matching idempotent confirmation replay; Custodian compiles. Replayed the existing confirmation through the API: contract reserved credit is now `0`, used credit `10000`; aggregate credit balances did not change.
- Custodian `LoanContract` remains mapped to the canonical `loan_contract` table; its generated outstanding-quantity and reserved-credit column definitions now match `db/02_custodian_db.sql`. No table rename or balance adjustment was made.
- Replayed the identical U2 event once: diagnostic consumer saw both copies, application offset advanced, inbox stayed one row, and balances/movement count did not change.
- Malformed U1 checksum (`000`, expected `058`) was rejected at request-topic offset `4` as `FIX_CHECKSUM_MISMATCH`; no request, inbox, or outbox business row was created.
- Live schema drift repaired to match canonical DDL: Custodian reservation `remaining_quantity` and contract `outstanding_quantity` are generated; Borrower message-log timestamp/version defaults restored. Existing rows were preserved.
- Local services authenticated with config-derived, process-only JWT overrides; no secrets were logged.
- Correlated logs: `app_logs/live-u1-bb-ba-ak-u2-20261111-1255/`.

## Remaining gaps

- Read-only local MySQL found one Custodian, one active MSLA, and one currently eligible Borrower/MSLA; no inactive/expired or second-Custodian records existed. API probes: Custodian 401; Borrower eligibility 403 (Custodian denied the relayed token). Health was UP; those API checks remain unverified.
- Custodian listing includes registered setup parties, mandated lenders before an MSLA, and historical agreement parties, excluding Custodian organizations. MSLA creation rejects borrowers outside caller scope. `SetupIT` covers own/other Custodian, no-MSLA setup, and history; compiled but not run because bootstrap alters schema/grants.
- Borrower tests passed 10/10, including inactive MSLA rejection; Custodian request-handler tests passed 6/6. Broader suites were not run; no lender-facing APIs exist.
- FIX `AE` remains unimplemented; no approval or settlement application logic changed.
