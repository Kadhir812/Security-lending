# Frontend Generation Context for Claude

## Purpose

Use this document together with the existing frontend design supplied in Claude. Generate the frontend for the **Borrower** and **Custodian** web applications against the existing Spring Boot backend. Do not invent backend routes, authentication behavior, or database fields. Use the endpoint catalog and DTO source files below as the contract.

The backend is a local securities-lending platform:

```text
Security Master / Market Data :8080
Custodian Service             :8081
Borrower Service              :8082
Valuation and Accrual         :8083
Kafka                         :9092
```

The frontend primarily calls ports `8081` and `8082`. Security Master and Valuation are backend-to-backend services.

## Frontend implementation requirements

- Preserve the provided visual design.
- Build two role-aware experiences:
  - Borrower portal
  - Custodian operations portal
- Use a typed API client rather than scattering `fetch` calls through components.
- Put the backend base URL in environment configuration.
- Send `Authorization: Bearer <JWT>` on every protected request.
- Send and display `X-Correlation-ID` when returned by the backend.
- Handle `401`, `403`, `404`, `409`, `422`, and `503` distinctly.
- Never display or hard-code JWT secrets in frontend code.
- Do not assume that a successful HTTP request means the workflow is complete; display the returned business status.
- Use server-provided identifiers and timestamps. Do not generate fake contract references, request references, prices, or status values.
- Make list pages paginated using the returned `PageResponse` fields.
- Use confirmation dialogs for approve, reject, close, fail, simulated-confirmation, and payment actions.
- Disable duplicate submissions while a request is in progress.
- Show loading, empty, validation, unauthorized, forbidden, conflict, unavailable, and success states.

## Authentication

The backend uses stateless local JWT authentication with HMAC-SHA256. There is no login endpoint in these services and no external OAuth/Keycloak server.

The frontend must receive a JWT from the project’s existing authentication/bootstrap flow or development token setup. Do not implement a fake login that creates JWTs in the browser.

Every secured request:

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

### Borrower JWT

Used for Borrower APIs and borrower-authorized Custodian APIs.

```text
issuer: securities-lending
audience: borrower-service
required role: ROLE_TRADER
```

The backend loads user roles from the database. JWT role claims must not be treated as the source of truth.

### Custodian JWT

Used for normal Custodian APIs.

```text
issuer: securities-lending
audience: custodian-service
required role: ROLE_CUSTODIAN_OPERATOR
```

Borrower-specific Custodian routes use `ROLE_TRADER`.

### HTTP status meanings

| Status | Frontend behavior |
|---|---|
| `200`, `201` | Show success and update the relevant data |
| `400` | Show request validation error |
| `401` | Clear the session/token and show authentication required |
| `403` | Show access denied; do not retry automatically |
| `404` | Show resource not found or stale workflow |
| `409` | Show business conflict and refresh the resource |
| `422` | Show ineligible calculation/workflow input |
| `503` | Show service unavailable and offer retry |

## Core workflow

```text
Borrower submits loan request
        |
        v
Custodian receives request through Kafka
        |
        v
Custodian operator approves or rejects
        |
        v
Custodian calls Valuation internally
        |
        v
Custodian publishes decision through Kafka
        |
        v
Borrower receives APPROVED or REJECTED decision
        |
        v
Borrower views contract and performs settlement/collateral actions
```

Kafka delivery is asynchronous. After submitting or approving, the frontend should poll the relevant GET endpoint or provide a refresh action. Do not assume the other service has updated immediately.

## Business statuses

### Loan request status

The backend uses values including:

```text
PENDING
APPROVED
REJECTED
```

Borrower responses also include a submission status and optional response reason. Render statuses with clear badges and do not infer status from the HTTP status code.

### Common workflow status concepts

The UI should be prepared to display:

```text
PENDING
IN_PROGRESS
CONFIRMED
FAILED
RELEASED
CLOSED
REJECTED
```

Use the exact value returned by each endpoint when available.

# Borrower frontend

Base URL:

```text
http://localhost:8082
```

## Borrower screens

Recommended screens:

1. Dashboard
2. Eligible MSLA selection
3. Submit loan request
4. My loan requests
5. Loan request details
6. Approved contract details
7. Cash collateral instruction
8. Settlement progress
9. Margin calls and top-ups
10. Securities return
11. Fee charges and payments
12. Final collateral release

## Borrower API catalog

All routes below require a borrower JWT unless explicitly stated.

### Foundation

```http
GET /api/foundation/status
```

Use for a service/status indicator. Do not expose internal implementation details.

### Eligible MSLA

```http
GET /api/loan-requests/eligible-mslas
```

Returns MSLA options available to the authenticated borrower.

### Loan requests

```http
POST /api/loan-requests
GET  /api/loan-requests
GET  /api/loan-requests/{requestReference}
GET  /api/loan-requests/{requestReference}/contract
```

Submit request body:

```json
{
  "mslaReference": "MSLA-001",
  "securityIsin": "INE002A01018",
  "quantity": 100,
  "requestedSettlementDate": "2026-11-12",
  "settlementCurrency": "INR",
  "locateReference": "LOCATE-001"
}
```

Validation:

```text
mslaReference: required, maximum 80 characters
securityIsin: required, exactly 12 uppercase letters/numbers
quantity: required, positive integer
requestedSettlementDate: required ISO date YYYY-MM-DD
settlementCurrency: required, exactly 3 uppercase letters
locateReference: optional, maximum 80 characters
```

Loan request response shape:

```json
{
  "requestReference": "SIM-SCENARIO1-LOAN-20261110",
  "mslaReference": "SIM-MSLA-001",
  "locateReference": "LOCATE-001",
  "securityIsin": "INE002A01018",
  "quantity": 100,
  "requestedSettlementDate": "2026-11-12",
  "settlementCurrency": "INR",
  "submissionStatus": "SUBMITTED",
  "status": "PENDING",
  "responseReason": null,
  "respondedAt": null,
  "requestedAt": "2026-11-10T07:19:46.331474Z",
  "version": 0
}
```

The exact enum values returned by the backend are authoritative.

Contract response shape:

```json
{
  "requestReference": "SIM-SCENARIO1-LOAN-20261110",
  "transactionReference": "CTR-...",
  "contractSnapshot": {},
  "receivedAt": "2026-11-10T07:20:00Z"
}
```

Render `contractSnapshot` as structured contract data, but tolerate additional fields because it is a JSON snapshot.

### Cash collateral

```http
POST /api/loan-requests/{requestReference}/collateral/cash-instructions
```

Request body type:

```text
CashCollateralInstructionRequest
```

Read the exact fields from:

```text
backend/borrower_service/src/main/java/com/hexaware/borrower_service/dto/request/CashCollateralInstructionRequest.java
```

### Contract settlement

```http
GET /api/contracts/{transactionReference}/settlement
```

Display the settlement timeline and current state.

### Margin calls

```http
GET  /api/contracts/{transactionReference}/margin-calls/{marginCallReference}
GET  /api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups
POST /api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups
```

Use the returned margin-call reference; do not construct one in the UI.

### Securities return

```http
GET  /api/contracts/{transactionReference}/returns
POST /api/contracts/{transactionReference}/returns
```

Request body type:

```text
SecuritiesReturnRequest
```

### Lending fees and payments

```http
GET  /api/contracts/{transactionReference}/charges
GET  /api/contracts/{transactionReference}/payments
POST /api/contracts/{transactionReference}/payments
```

Request body types:

```text
FeePaymentRequest
FeePaymentAllocationRequest
```

### Final collateral release

```http
GET  /api/contracts/{transactionReference}/collateral-releases
POST /api/contracts/{transactionReference}/collateral-releases
```

Request body type:

```text
FinalCollateralReleaseRequest
```

## Borrower ownership rule

The backend checks that the authenticated user's organization owns the requested resource. The frontend must not allow a user to edit or submit data for another organization, but the backend remains the final authority.

# Custodian frontend

Base URL:

```text
http://localhost:8081
```

## Custodian screens

Recommended screens:

1. Operations dashboard
2. Pending loan-request queue
3. Loan request review
4. Organization management
5. Mandates
6. Lender inventory
7. MSLA agreements
8. Credit limits and history
9. Collateral haircuts
10. Lending fees
11. Active contracts
12. Settlement operations
13. Margin calls
14. Recalls
15. Fee payments
16. Collateral-release approval queue
17. Audit/activity timeline

## Custodian API catalog

### Foundation

```http
GET /api/foundation/status
```

### Loan-request operations

```http
GET  /api/loan-requests
GET  /api/loan-requests/{requestReference}
POST /api/loan-requests/{requestReference}/approve
POST /api/loan-requests/{requestReference}/reject
```

Reject body:

```json
{
  "reason": "Insufficient eligible inventory"
}
```

Approve response shape:

```json
{
  "requestReference": "SIM-SCENARIO1-LOAN-20261110",
  "status": "APPROVED",
  "reason": null,
  "contractReference": "CTR-...",
  "loanMarketValue": 10000.00,
  "requiredCashCollateral": 10500.00,
  "respondedAt": "2026-11-10T10:19:00Z"
}
```

The approval call may return a downstream `503` if Valuation is unavailable or rejects the calculation.

### Borrower MSLA access

These are called by the Borrower service with a borrower JWT, and are useful for borrower eligibility data:

```http
GET /api/borrower/mslas
GET /api/borrower/mslas/{reference}
```

### Organizations

```http
POST /api/organizations
GET  /api/organizations/{reference}
PUT  /api/organizations/{reference}
GET  /api/organizations
```

### Mandates

```http
POST /api/mandates
GET  /api/mandates/{reference}
PUT  /api/mandates/{reference}
GET  /api/mandates
```

### Lender inventory

```http
POST /api/inventory
GET  /api/inventory/{lenderReference}/{securityIsin}
GET  /api/inventory
PUT  /api/inventory/{lenderReference}/{securityIsin}
```

### MSLA agreements

```http
POST /api/mslas
GET  /api/mslas/{reference}
PUT  /api/mslas/{reference}
GET  /api/mslas
```

### Credit limits

```http
POST /api/credit-limits/{mslaReference}
GET  /api/credit-limits/{mslaReference}
PUT  /api/credit-limits/{mslaReference}
GET  /api/credit-limits/{mslaReference}/history
GET  /api/credit-limits
```

### Collateral haircuts

```http
POST /api/mslas/{mslaReference}/collateral-haircuts
GET  /api/mslas/{mslaReference}/collateral-haircuts
GET  /api/mslas/{mslaReference}/collateral-haircuts/{haircutId}
PUT  /api/mslas/{mslaReference}/collateral-haircuts/{haircutId}
```

### Lending fees

```http
POST /api/mslas/{mslaReference}/lending-fees
GET  /api/mslas/{mslaReference}/lending-fees
GET  /api/mslas/{mslaReference}/lending-fees/{feeId}
PUT  /api/mslas/{mslaReference}/lending-fees/{feeId}
GET  /api/mslas/{mslaReference}/lending-fees/effective
```

### Contract settlement

```http
GET  /api/contracts/{transactionReference}/settlement
POST /api/contracts/{transactionReference}/settlement/cash-instructions
POST /api/contracts/{transactionReference}/settlement/cash-instructions/simulated-confirm
POST /api/contracts/{transactionReference}/settlement/securities/simulated-confirm
POST /api/contracts/{transactionReference}/settlement/returns/simulated-confirm
POST /api/contracts/{transactionReference}/settlement/returns/simulated-fail
POST /api/contracts/{transactionReference}/settlement/collateral-releases/simulated-confirm
POST /api/contracts/{transactionReference}/settlement/collateral-releases/simulated-fail
```

The simulated routes are development/demo workflow controls. Put them behind an obvious simulation/development UI treatment.

### Accruals and fee payments

```http
POST /api/contracts/{transactionReference}/accruals
GET  /api/contracts/{transactionReference}/charges
GET  /api/contracts/{transactionReference}/payments
POST /api/contracts/{transactionReference}/payments/{paymentReference}/confirm
POST /api/contracts/{transactionReference}/payments/{paymentReference}/fail
```

### Margin calls

```http
POST /api/contracts/{transactionReference}/margin-calls/evaluate
POST /api/contracts/{transactionReference}/margin-calls/mtm
GET  /api/contracts/{transactionReference}/margin-calls
GET  /api/contracts/{transactionReference}/margin-calls/{callReference}
```

### Recalls

```http
POST /api/contracts/{transactionReference}/recalls
GET  /api/contracts/{transactionReference}/recalls
GET  /api/contracts/{transactionReference}/recalls/{recallReference}
```

### Contract closure

```http
POST /api/contracts/{transactionReference}/close
```

### Collateral-release approval

```http
GET  /api/collateral-releases/requests
POST /api/contracts/{transactionReference}/collateral-releases/{releaseReference}/approve
```

## Custodian authorization rules

The backend enforces:

```text
normal Custodian APIs:
  ROLE_CUSTODIAN_OPERATOR

/api/borrower/**:
  ROLE_TRADER

resource access:
  authenticated organization must match resource organization

mandate operations:
  custodian operator role plus active mandate access
```

Hide or disable actions the current user cannot perform, but still handle backend `403` responses.

# Shared response and UI conventions

## Pagination

List endpoints commonly return:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Use zero-based page indexes when calling the backend if the endpoint supports pagination.

## Dates and money

- Dates use `YYYY-MM-DD`.
- Timestamps are ISO-8601 UTC strings.
- Money values should be displayed with two decimal places and the returned currency.
- Quantities are positive integers.
- ISIN values are uppercase 12-character strings.
- Do not use floating-point arithmetic for displayed financial totals when a decimal library is available.

## Error presentation

The backend may return Spring Problem Details JSON, for example:

```json
{
  "title": "Service Unavailable",
  "status": 503,
  "detail": "Valuation service rejected or could not process approval",
  "instance": "/api/loan-requests/..."
}
```

Display `detail` to the user when safe; otherwise use a friendly fallback message and retain the correlation ID for support.

## Recommended API client behavior

```text
request()
  -> attach bearer token
  -> attach JSON content type for body requests
  -> attach/request correlation ID
  -> parse JSON
  -> map HTTP errors to typed frontend errors
  -> return data
```

Do not retry:

```text
401, 403, 400, 409, 422
```

Retry only carefully for:

```text
503 or network timeout
```

Never automatically retry approve, reject, payment, close, or simulated-confirmation actions unless the API operation is explicitly known to be idempotent.

# Backend source of truth

Use these files when exact field names or validation rules are needed:

```text
backend/borrower_service/src/main/java/com/hexaware/borrower_service/controller/
backend/borrower_service/src/main/java/com/hexaware/borrower_service/dto/request/
backend/borrower_service/src/main/java/com/hexaware/borrower_service/dto/response/
backend/custodian_service/src/main/java/com/hexaware/custodian_service/controller/
backend/custodian_service/src/main/java/com/hexaware/custodian_service/dto/request/
backend/custodian_service/src/main/java/com/hexaware/custodian_service/dto/response/
backend/*/src/main/resources/application.properties
```

Important backend behavior:

- Borrower and Custodian are stateless JWT resource servers.
- Security Master is REST-only and currently does not require JWT for normal market-data routes.
- Custodian creates a separate short-lived `valuation-service` JWT for Valuation calls.
- Loan decisions are asynchronous through Kafka.
- The database is split by microservice; do not query another service's database from the frontend.
- Do not expose database credentials, JWT secrets, Kafka credentials, or internal service-to-service tokens in browser code.

# Development environment

Default local URLs:

```text
Borrower:   http://localhost:8082
Custodian:  http://localhost:8081
Market:     http://localhost:8080
Valuation:  http://localhost:8083
```

If the frontend runs on another origin, configure CORS or use a development proxy. Do not solve CORS by disabling browser security in production.

# Acceptance criteria for the generated frontend

1. Borrower can view eligible MSLAs.
2. Borrower can submit a valid loan request.
3. Borrower can see `PENDING`, `APPROVED`, or `REJECTED`.
4. Borrower can open the approved contract.
5. Custodian can view the loan-request queue.
6. Custodian can open request details.
7. Custodian can approve or reject with confirmation and loading states.
8. Custodian approval displays contract reference, market value, and required collateral.
9. Both portals show backend errors clearly.
10. The UI never fabricates missing backend data.
11. All protected calls send the bearer token.
12. Responsive behavior matches the supplied design.
