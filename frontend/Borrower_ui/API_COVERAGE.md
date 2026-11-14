# Borrower service — API coverage

Source of truth: Java controller classes under `backend/borrower_service/src/main/java/com/hexaware/borrower_service/controller/` on the current checkout. The UI uses the Borrower service on port 8082 through `/api`.

## Mapped Borrower endpoints (23 mappings)

| # | HTTP | Path | Frontend client / surface |
|---|---|---|---|
| 1 | GET | `/api/foundation/status` | `foundation.status` · status indicator |
| 2 | POST | `/api/auth/signup` | `auth.signup` · demo account registration |
| 3 | POST | `/api/auth/login` | `auth.login` · sign in |
| 4 | GET | `/api/auth/me` | Not currently called by the UI |
| 5 | GET | `/api/loan-requests/eligible-mslas` | `loans.eligible` · loan prerequisites |
| 6 | GET | `/api/loan-requests` | `loans.list` · borrower-scoped request pages |
| 7 | POST | `/api/loan-requests` | `loans.submit` · requires `Idempotency-Key` |
| 8 | GET | `/api/loan-requests/{reference}` | `loans.get` · request details |
| 9 | POST | `/api/loan-requests/{reference}/collateral-inquiries` | `loans.collateralInquiry` · FIX BB request |
| 10 | GET | `/api/loan-requests/{reference}/collateral-reports` | `loans.collateralReports` · FIX BA reports |
| 11 | GET | `/api/loan-requests/{reference}/contract` | `loans.contract` · single contract projection |
| 12 | POST | `/api/loan-requests/{reference}/collateral/cash-instructions` | `loans.cash` · initial CASH instruction |
| 13 | GET | `/api/contracts/{transactionReference}/settlement` | `contracts.settlement` · settlement projection |
| 14 | GET | `/api/contracts/{transactionReference}/margin-calls/{marginCallReference}` | `contracts.marginCall` · known margin call |
| 15 | GET | `/api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups` | `contracts.topUps` · top-up history |
| 16 | POST | `/api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups` | `contracts.topUp` · submit CASH top-up |
| 17 | GET | `/api/contracts/{transactionReference}/returns` | `contracts.returnProgress` · recalls and returns |
| 18 | POST | `/api/contracts/{transactionReference}/returns` | `contracts.submitReturn` · submit securities return |
| 19 | GET | `/api/contracts/{transactionReference}/charges` | `contracts.charges` · fee charge ledger |
| 20 | GET | `/api/contracts/{transactionReference}/payments` | `contracts.payments` · payment history |
| 21 | POST | `/api/contracts/{transactionReference}/payments` | `contracts.submitPayment` · requires `Idempotency-Key` |
| 22 | GET | `/api/contracts/{transactionReference}/collateral-releases` | `contracts.releases` · release state |
| 23 | POST | `/api/contracts/{transactionReference}/collateral-releases` | `contracts.requestRelease` · final release request |

## Confirmed API gaps

- There is no `/api/locates` controller. `locateReference` is optional in `LoanRequestSubmission`; the UI disables the selector and omits the field.
- There is no `GET /api/contracts` portfolio route. The UI lists approved loan requests and resolves each projection through `/api/loan-requests/{reference}/contract`.
- There is no contract-wide `GET /api/contracts/{transactionReference}/margin-calls` route. Margin details and top-ups require a known `marginCallReference`, which the UI requests explicitly.
- BB/BA inquiry and report routes are present. The POST response (`inquiryId`, `requestReference`, `mslaReference`, `status`) is an asynchronous receipt, not a report or cash settlement. The report response contains `reportId`, `inquiryId`, `requestReference`, `mslaReference`, `status`, `collateralType`, `currency`, `haircutPercentage`, `coveragePercentage`, and `receivedAt`.

Backend-owned actions intentionally excluded from the Borrower UI include loan approval/rejection, margin-call creation, settlement simulation/confirmation, payment confirmation/failure, release approval and contract closure. Kafka and FIX processing remain inside the backend.

## Request and authentication DTOs

- `DemoSignupRequest`: `username`, `organizationName`, `email`, `password`. Role assignment is backend-controlled (`TRADER`).
- `LoginRequest`: `email`, `password`.
- `AuthResponse`: `accessToken`, `tokenType`, `expiresIn`, `user`.
- `LoanRequestSubmission`: `mslaReference`, `securityIsin`, `quantity`, `requestedSettlementDate`, `settlementCurrency`, optional `locateReference`.
- `CashCollateralInstructionRequest`: `instructionReference`, `amount`, `currency`, `collateralType: "CASH"`.
- `SecuritiesReturnRequest`: `instructionReference`, optional `recallReference`, `quantity`.
- `FeePaymentRequest`: `amount`, `currency`, `allocations` (each `calculationResultReference`, `itemKey`, `amount`); payment idempotency is sent in the header.
- `FinalCollateralReleaseRequest`: `releaseReference`.

The shared Axios client attaches the Borrower JWT to protected requests, excludes it on sign-up/login, and clears the session on a protected 401. Development proxy: Borrower UI `:5174` → `http://localhost:8082`; Custodian UI `:5173` → `http://localhost:8081`.

Static mapping checks are not runtime tests. Live API round trips require running services, configured dependencies and valid local accounts.
