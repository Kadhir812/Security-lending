# Custodian Portal: Lifecycle, Inputs, and Pages

## Purpose and boundary

This document describes the Custodian operations portal using the current
Custodian REST API. The portal configures lending relationships, reviews
Borrower-submitted requests, operates settlement and active-loan workflows,
and monitors returned statuses. It must not publish Kafka messages directly,
create FIX messages, query MySQL, or invent server-owned identifiers,
valuations, balances, or statuses.

Custodian API base URL: `http://localhost:8081`

## End-to-end lifecycle

```text
Configure organizations, lender inventory, and MSLA & Lending Setup
        |
        v
Create or reuse the active mandate, then create the MSLA, credit limit, CASH haircut, and fee schedule together
        |
        v
Borrower locate (API not available yet) and loan request
        |
        v
Custodian reviews request, then approves or rejects
        |
        v
Custodian receives collateral inquiry and generates report (backend flow)
        |
        v
Borrower submits CASH instruction; Custodian monitors/settles collateral
        |
        v
Custodian confirms initial securities delivery
        |
        v
Manage active loan: MTM, margin, recall, returns, fees, and payments
        |
        v
Approve final collateral release and close the returned contract
```

Loan decisions and settlement notifications flow asynchronously between
services. Display the business status returned by each endpoint and refresh
the relevant view; a successful submission alone does not complete the
downstream workflow.

## What the Custodian operator enters

| Stage | Custodian input/action | API and key fields |
|---|---|---|
| Organization | Create/update organization reference, name, type, status; use the returned/current version on updates where applicable. | `POST /api/organizations`, `PUT /api/organizations/{reference}`. |
| MSLA & Lending Setup | Lender and Borrower, either a new mandate or an existing active mandate, agreement dates and terms, credit limit and reason, and annual fee. The Custodian organization comes from the authenticated identity. The backend creates/reuses an ACTIVE mandate and atomically creates the ACTIVE MSLA, credit limit, zero-percent CASH haircut, and agreement-wide fee schedule. | `POST /api/mslas/setup`. Existing mandate management APIs remain available for legacy records. |
| Inventory | Lender organization, security ISIN, total quantity; updates include the current version. | `POST /api/inventory`, `PUT /api/inventory/{lenderReference}/{securityIsin}`. |
| MSLA | For new agreements, use the combined setup above. Existing draft agreements remain accessible through the MSLA API. | `GET /api/mslas`, `GET /api/mslas/{reference}`, existing `POST /api/mslas` and `PUT /api/mslas/{reference}`. |
| Credit limit | Created in the combined setup; later changes require a reason and current version. | `POST /api/credit-limits/{mslaReference}`, `PUT /api/credit-limits/{mslaReference}`. |
| Haircut | New CASH agreements receive a dated 0% haircut. Other supported collateral types remain outside this phase. | `POST/PUT /api/mslas/{mslaReference}/collateral-haircuts[/{haircutId}]`. |
| Lending fee | New agreements receive one active agreement-wide annual fee schedule; later security-specific schedules remain configurable. | `POST/PUT /api/mslas/{mslaReference}/lending-fees[/{feeId}]`. |
| Loan decision | Review request details; approve with no invented valuation fields, or reject with a reason. | `POST /api/loan-requests/{requestReference}/approve`; reject body is `{ "reason": "..." }`. |
| Simulated settlement | For local demos only: instruction reference and external confirmation reference; failure actions also require a reason. | Explicit `/simulated-confirm` and `/simulated-fail` endpoints. These are demo controls, not production settlement evidence. |
| MTM | Trigger MTM with an `Idempotency-Key`, or request evaluation. | `POST .../margin-calls/mtm`, `POST .../margin-calls/evaluate`. |
| Recall | Recall reference, positive quantity, due timestamp, and reason. | `POST /api/contracts/{transactionReference}/recalls`. |
| Payment processing | Confirm with an external confirmation reference, or fail with a reason. | `POST /api/contracts/{transactionReference}/payments/{paymentReference}/confirm|fail`. |
| Release approval | Approve a Borrower-submitted release by transaction and release references. | `POST /api/contracts/{transactionReference}/collateral-releases/{releaseReference}/approve`. |
| Contract closure | Closure reference and reason after return, fee, settlement, and collateral prerequisites are satisfied. | `POST /api/contracts/{transactionReference}/close`. |

## Pages, recommended build order

### 1. Operations dashboard

Show service status, pending loan requests, settlement work, active margin
calls, payment exceptions, pending collateral-release requests, and recently
closed/updated items where supported. Do not promise dashboard counts or an
activity feed until list APIs exist for them.

### 2. Organizations

Search/list and manage organizations within the authenticated Custodian's
scope. Inputs: organization reference, display name, organization type, and
status. Never allow the user to select or change the owning Custodian identity
unless the backend explicitly supports it.

### 3. Borrower directory

List registered Borrower organizations visible to the authenticated Custodian.
Show organization status and, for a selected Borrower, the number of currently
active MSLAs scoped to this Custodian. Provide a shortcut to the combined MSLA
setup form with that Borrower preselected. Do not treat directory visibility
as proof that an active agreement exists.

### 4. Lender inventory

Select lender and security; show total, reserved, on-loan, and available
quantities returned by the service. Create/update total quantity only with
optimistic version data. Remove an inventory row only after explicit
confirmation; the backend blocks removal while shares are encumbered or
reservation history references the row. Avoid editing derived quantities.

### 5. Security Master and market prices

Browse active securities and inspect latest and recent market prices from the
shared Security Market service. Display quote timestamp and stale status; do
not calculate valuations or treat an informational quote as approval evidence.

### 6. MSLA & lending setup

Use one form to create a new lender mandate or select a currently active one,
then set up the Borrower MSLA, credit limit, 0% CASH haircut, and annual fee.
The setup endpoint commits new records atomically and activates the MSLA
immediately. Display returned terms and references; do not calculate credit,
collateral, or fee values in the browser.

### 7. Locate and loan request desk

Show the paginated loan-request inbox and Custodian-visible pending Locate
inbox together. Include details on row selection and deliberate approval or
rejection. Locate approval confirms availability without reserving shares;
loan approval rechecks and reserves inventory. Provide refresh because Kafka
delivery is asynchronous.

### 8. Loan request review

Open the server's request detail. Present the authoritative request/MSLA and
inventory context, then offer approve/reject with an explicit confirmation.
Reject requires a reason. Approval calls Valuation internally; show the
returned contract reference, loan market value, required CASH, status, and
response time. A downstream `503` should leave the decision visibly
incomplete and allow a deliberate retry after refresh.

### 9. Collateral inquiry/report visibility

The Borrower collateral inquiry (BB) and generated report (BA) are processed
by the messaging handlers. There is currently no Custodian REST endpoint
listed for querying incoming inquiries or reports. Treat an inquiry/report
inbox page as a UI/API gap until a supported read endpoint is provided; do not
read inbox tables directly or manually author a BA report.

### 10. Settlement operations

Open a contract's settlement progress and display CASH and securities
instructions, status, amounts/quantities, and timestamps. A Borrower-submitted
CASH instruction can be monitored and, in the local demo, confirmed with an
instruction reference and confirmation reference. The initial securities
confirmation requires its own instruction and confirmation references and
must only be offered when the service says full required CASH is settled.

Keep all simulated-confirm/fail buttons behind an explicit development/demo
mode, confirmation dialog, and visible simulation label. Never represent a
simulation confirmation as an external bank/custodian attestation.

### 11. Active contracts and margin calls

Display contract status, outstanding quantity, settlement, and collateral.
Trigger MTM with an idempotency key when required; evaluate/list/get margin
calls using returned call references. The current controller does not expose
a general contract-list endpoint, so an Active Contracts portfolio page
needs an existing request-to-contract navigation or a future list API.

### 12. Recalls and returns

Create a recall using a new caller-provided recall reference, quantity, due
time, and reason; inspect its returned status. Monitor the Borrower's return
instruction, then use Custodian simulated confirm/fail controls only in demo
mode. Do not mark securities returned in the UI before the service confirms.

### 13. Fees and payments

Inspect charges and payment records. Confirm or fail a specific returned
payment reference, recording the external confirmation reference or failure
reason. Use a dialog and refresh the payment list after processing.

### 14. Collateral-release approval queue

List Borrower requests, inspect the contract/release context, then approve
the release using the supplied transaction and release references. Monitor
the settlement instruction; simulation controls for external confirmation or
failure remain demo-only.

### 15. Contract close and activity

Close only after the backend validates all prerequisites. Require a unique
closure reference and reason, then show the returned status. A unified
audit/activity timeline is useful, but there is no general activity endpoint
in the current catalog; use only available resource-specific histories.

### 16. Lender cash account

Select an active lender within the current Custodian mandate scope. Show cash,
restricted collateral, available cash, and recent ledger entries. External
deposit and withdrawal records require operator confirmation, a source
reference, and an idempotency key; withdrawals cannot consume restricted
collateral. This is an operational ledger, not a bank connection.

## API map

| Use | Method and path |
|---|---|
| Service status | `GET /api/foundation/status` |
| Security Master and prices | `GET /api/reference/securities`; `GET /api/reference/securities/{isin}/prices/latest`; `GET /api/reference/securities/{isin}/prices?from={instant}&to={instant}` |
| Lender cash account | `GET /api/lenders/{lenderReference}/cash-accounts/{currency}`; append `/transactions`; `POST .../deposits` or `/withdrawals` with `Idempotency-Key` |
| Loan requests | `GET /api/loan-requests?page=0&size=20`; `GET /api/loan-requests/{requestReference}` |
| Locate requests | `GET /api/locate-requests`; `POST /api/locate-requests/{reference}/approve`; `POST /api/locate-requests/{reference}/reject` |
| Collateral inquiry visibility | `GET /api/collateral-inquiries?page=0&size=20` (processed BB records for the authenticated Custodian, with BA outbox publication status) |
| Loan decisions | `POST /api/loan-requests/{requestReference}/approve`; `POST /api/loan-requests/{requestReference}/reject` |
| Organizations | `GET/POST /api/organizations`; `GET/PUT /api/organizations/{reference}` |
| Borrower directory | `GET /api/organizations/borrowers?page=0&size=20`; `GET /api/organizations/borrowers/{reference}/active-msla-count` |
| Mandates | `GET/POST /api/mandates`; `GET/PUT /api/mandates/{reference}` |
| Inventory | `GET/POST /api/inventory`; `GET/PUT /api/inventory/{lenderReference}/{securityIsin}`; `DELETE /api/inventory/{lenderReference}/{securityIsin}?version={version}` |
| MSLAs | `GET /api/mslas`; `POST /api/mslas/setup` for atomic mandate/MSLA/risk/fee setup; legacy `POST /api/mslas` and `PUT /api/mslas/{reference}` remain available. |
| Credit limits | `GET /api/credit-limits`; `GET/POST/PUT /api/credit-limits/{mslaReference}`; append `/history` for history |
| Haircuts | `GET/POST /api/mslas/{mslaReference}/collateral-haircuts`; `GET/PUT .../{haircutId}` |
| Lending fees | `GET/POST /api/mslas/{mslaReference}/lending-fees`; `GET/PUT .../{feeId}`; `/effective?securityIsin=...&businessDate=YYYY-MM-DD` |
| Settlement | `GET /api/contracts/{transactionReference}/settlement`; POST under `/cash-instructions`, `/cash-instructions/simulated-confirm`, `/securities/simulated-confirm`, `/returns/simulated-confirm|simulated-fail`, `/collateral-releases/simulated-confirm|simulated-fail` |
| Accrual/charges | `POST /api/contracts/{transactionReference}/accruals`; `GET .../charges` |
| Payments | `GET /api/contracts/{transactionReference}/payments`; `POST .../{paymentReference}/confirm|fail` |
| Margin | `POST /api/contracts/{transactionReference}/margin-calls/evaluate|mtm`; `GET .../margin-calls[/{callReference}]` |
| Recalls | `POST/GET /api/contracts/{transactionReference}/recalls`; append `/{recallReference}` for detail |
| Collateral-release queue | `GET /api/collateral-releases/requests`; approve under `/api/contracts/{transactionReference}/collateral-releases/{releaseReference}/approve` |
| Close contract | `POST /api/contracts/{transactionReference}/close` |
| Borrower-eligible MSLA lookup | `GET /api/borrower/mslas[/{reference}]` (Borrower-authorized route, not a Custodian operations page) |

List endpoints with `PageResponse` use zero-based `page` and `size` and return
`content`, `page`, `size`, `totalElements`, and `totalPages`. Some resource
lists are plain arrays; use each endpoint's actual response shape.

## Authentication and operational safeguards

- Protected Custodian operations use a Custodian JWT, normally with
  the `custodian-service` audience and `ROLE_CUSTODIAN_OPERATOR` or
  `ROLE_ADMINISTRATOR`. Borrower-specific `/api/borrower/**` routes require a
  Borrower token with the `custodian-service` audience and local `ROLE_TRADER`
  assignment. Borrower-to-Custodian MSLA lookups forward the original token,
  so that Borrower token also needs the `borrower-service` audience.
- The services must trust the same signing key and issuer for forwarded
  Borrower tokens. Tokens must have a valid signature, issuer, audience,
  expiry, subject, and matching organization claim. Roles come from local
  database assignments, not token role claims; never allow the UI to select
  the caller organization or manufacture role claims.
- The services do not provide a login/token endpoint. Use the existing
  trusted authentication/bootstrap flow; never mint tokens or put signing
  secrets in the browser. The browser should use a Custodian-audience token for
  Custodian operator pages, not a Borrower token.
- The backend enforces organization and mandate scope. Hide unavailable
  actions in the UI but always handle `403`.
- Treat `401` as a missing, invalid, expired, or otherwise unaccepted token;
  renew/re-authenticate through the trusted flow. A `403` is an authenticated
  identity that lacks permission or agreement scope.
- Use server-provided references, versions, timestamps, status, and financial
  values. Never manufacture an approval result, collateral requirement, or
  settlement status in the frontend.
- Require confirmation for approve, reject, simulated confirmation/failure,
  payment processing, release approval, and closure.
- Disable duplicate submissions; use idempotency keys where the API requires
  them. Do not automatically retry decisions, payments, settlement, or close
  unless the operation's idempotency contract allows it.
- Handle `400`, `401`, `403`, `404`, `409`, `422`, and `503` distinctly;
  refresh after conflicts and show returned business errors.

## Current API gaps

- Locate create/review endpoints are not exposed through REST.
- There is no unified active-contract list or unified audit/activity endpoint.
- There is no general active-contract list or unified audit/activity endpoint.
- Several settlement confirmation/failure endpoints are explicitly
  simulated demo operations; production external settlement integration is
  outside this UI contract.

## Backend source references

- Custodian controllers:
  `backend/custodian_service/src/main/java/com/hexaware/custodian_service/controller/`
- Custodian request DTOs:
  `backend/custodian_service/src/main/java/com/hexaware/custodian_service/dto/request/`
- Shared API context: `db/CLAUDE_FRONTEND_BACKEND_CONTEXT.md`
- Borrower counterpart: `db/BORROWER_FRONTEND_WORKFLOW.md`
