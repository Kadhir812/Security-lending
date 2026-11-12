# Updated Borrower service — API coverage

Source of truth: Java controller classes under `backend/borrower_service/src/main/java/com/hexaware/borrower_service/controller/` in the **uploaded `Security-lending-fixedapi.zip`**. Older `securities_lending_full_api_audit.md` describes another backend revision; this matrix uses the actual supplied controller mappings.

## All mapped Borrower endpoints (23 mappings)

| # | HTTP | Path (relative to Borrower :8082) | Axios module | Frontend surface |
|---|---|---|---|---|
| 1 | GET | `/api/foundation/status` | `foundation.status` | Global status bar / auth screen |
| 2 | GET | `/api/locates` | `locates.list` | Locate ledger and loan form |
| 3 | POST | `/api/locates` | `locates.submit` | Create locate confirmation |
| 4 | GET | `/api/locates/{reference}` | `locates.get` | Locate detail |
| 5 | GET | `/api/loan-requests/eligible-mslas` | `loans.eligible` | Eligible MSLAs / submission prerequisites |
| 6 | GET | `/api/loan-requests` | `loans.list` | Paginated request ledger / overview |
| 7 | POST | `/api/loan-requests` | `loans.submit` | Submit borrow; **Idempotency-Key** |
| 8 | GET | `/api/loan-requests/{reference}` | `loans.get` | Loan request detail |
| 9 | GET | `/api/loan-requests/{reference}/contract` | `loans.contract` | Approved loan projection / CASH collateral |
| 10 | POST | `/api/loan-requests/{reference}/collateral/cash-instructions` | `loans.cash` | Initial CASH collateral |
| 11 | GET | `/api/contracts` | `contracts.list` | Contract portfolio / contract selection |
| 12 | GET | `/api/contracts/{transactionReference}/settlement` | `contracts.settlement` | Settlement read-only projection |
| 13 | GET | `/api/contracts/{transactionReference}/margin-calls` | `contracts.marginCalls` | Contract margin calls |
| 14 | GET | `/api/contracts/{transactionReference}/margin-calls/{marginCallReference}` | `contracts.marginCall` | Margin call detail |
| 15 | GET | `/api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups` | `contracts.topUps` | Top-up instruction ledger |
| 16 | POST | `/api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups` | `contracts.topUp` | Submit CASH top-up confirmation |
| 17 | GET | `/api/contracts/{transactionReference}/returns` | `contracts.returnProgress` | Recalls and returns read-only projection |
| 18 | POST | `/api/contracts/{transactionReference}/returns` | `contracts.submitReturn` | Submit securities return confirmation |
| 19 | GET | `/api/contracts/{transactionReference}/charges` | `contracts.charges` | Lending fee charge ledger |
| 20 | GET | `/api/contracts/{transactionReference}/payments` | `contracts.payments` | Fee payment ledger |
| 21 | POST | `/api/contracts/{transactionReference}/payments` | `contracts.submitPayment` | Submit fee payment with **Idempotency-Key** |
| 22 | GET | `/api/contracts/{transactionReference}/collateral-releases` | `contracts.releases` | Release approval state |
| 23 | POST | `/api/contracts/{transactionReference}/collateral-releases` | `contracts.requestRelease` | Submit final release request confirmation |

Backend-owned actions intentionally excluded: accept/reject loans or locates, settlement simulation, MTM generation, raising margin calls, payment confirmation/failure, release approval/settlement and contract closure. Those are **Custodian** responsibilities.

## Request DTO correspondence

- `LocateSubmission`: `mslaReference`, `securityIsin`, `quantity`.
- `LoanRequestSubmission`: `mslaReference`, `securityIsin`, `quantity`, `requestedSettlementDate`, `settlementCurrency`, optional `locateReference`.
- `CashCollateralInstructionRequest`: `instructionReference`, `amount`, `currency`, `collateralType: "CASH"`.
- `SecuritiesReturnRequest`: `instructionReference`, optional `recallReference`, `quantity`.
- `FeePaymentRequest`: `amount`, `currency`, `allocations` (each `calculationResultReference`, `itemKey`, `amount`). Payment idempotency reference goes in the **header**, not the JSON DTO.
- `FinalCollateralReleaseRequest`: `releaseReference`.

## Gap compared with earlier audit

The **uploaded audit** references `POST /api/loan-requests/{reference}/collateral-inquiries` (FIX BB) and `GET /api/loan-requests/{reference}/collateral-reports` (FIX BA). **Neither mapping exists in the newer LoanRequestController.java** included in `Security-lending-fixedapi.zip`. The UI uses a clearly labeled API-pending screen rather than fabricating requests/responses. Please add/reinstate the controller mappings if those flows are meant to be browser-accessible.

No Borrower endpoints for a market ticker, per-security exposure treemap, historical portfolio valuation, live FIX journal, aggregate credit utilization, consolidated alerts or unified audit log. These UI widgets are **Demo data – API pending** or unavailable in Live mode. The Activity page derives a limited timeline from live request/contract timestamps, explicitly not a unified audit endpoint.

**Verification note:** Static inspection of controller mappings and form field names is not equivalent to running the backend. Live round-trip/API contract tests require a running local Java/Kafka/DB environment and a valid Borrower JWT.
