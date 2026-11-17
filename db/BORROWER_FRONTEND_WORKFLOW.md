# Borrower Portal: Lifecycle, Inputs, and Pages

## Purpose and boundary

This document describes the Borrower-facing experience using the current
Borrower and Custodian APIs. The portal submits and monitors Borrower actions;
it must not create FIX messages, publish to Kafka, access MySQL, or invent
server-owned references, prices, calculations, or statuses.

Borrower API base URL: `http://localhost:8082`

## End-to-end lifecycle

```text
Borrower submits Locate request
        |
        v
Custodian confirms current lender availability or rejects
        |
        v
Select the approved Locate and submit loan request
        |
        v
Custodian reviews and approves or rejects
        |
        v
Borrower requests collateral inquiry and receives report
        |
        v
Borrower submits CASH collateral instruction
        |
        v
Custodian confirms CASH and initial securities delivery
        |
        v
Borrower monitors active contract, margin, fees, and payments
        |
        v
Borrower requests securities return and final collateral release
        |
        v
Custodian confirms the return/release and closes the contract
```

Each submit/approve/settlement action may be asynchronous across services.
Show the response status, then refresh or poll the relevant GET endpoint;
submission acceptance does not mean the downstream workflow is complete.

## Borrower inputs by stage

| Stage | Borrower provides | Current API / limitation |
|---|---|---|
| Locate | Eligible MSLA, security ISIN, and positive whole quantity. | `POST /api/locates` with an `Idempotency-Key`; list/detail at `GET /api/locates` and `GET /api/locates/{locateReference}`. Custodian decisions arrive asynchronously. Approval confirms current availability, does not reserve inventory, and does not expire. |
| Loan request | Eligible MSLA, security ISIN, quantity, settlement date, currency, and optionally an existing locate reference. | `POST /api/loan-requests`; requires an `Idempotency-Key` header. `locateReference` is optional, max 80 characters. |
| Collateral inquiry | No body fields; choose one of the Borrower's own requests. | `POST /api/loan-requests/{requestReference}/collateral-inquiries`; report uses authoritative agreement data. |
| Read collateral report | No input; open a request's reports. | `GET /api/loan-requests/{requestReference}/collateral-reports`. |
| CASH instruction | Instruction reference, positive amount, 3-letter currency, and collateral type (CASH). | `POST /api/loan-requests/{requestReference}/collateral/cash-instructions`. Use the required amount/currency reported for the approved contract; do not calculate a replacement in the browser. |
| Margin top-up | Instruction reference, positive amount, currency, and collateral type. | `POST /api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups`; same `CashCollateralInstructionRequest` shape. |
| Securities return | Instruction reference, positive quantity, and optional recall reference. | `POST /api/contracts/{transactionReference}/returns`. |
| Fee payment | Total amount, currency, and one or more allocations with calculation result reference, item key, and amount. Also send the payment reference as `Idempotency-Key`. | `POST /api/contracts/{transactionReference}/payments`. Build allocations from returned charge data; never fabricate calculation references. |
| Final collateral release | A unique release reference, max 48 characters. | `POST /api/contracts/{transactionReference}/collateral-releases`. Custodian must subsequently process the release. |
| Contract closure | No Borrower input endpoint. | Closure is a Custodian action after return, fee, settlement, and collateral prerequisites are satisfied. |

### Loan request body

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

`locateReference` may be omitted. Other fields are required. The server
validates the MSLA's eligibility and the authenticated Borrower's ownership.
Use the request reference returned by the server for all subsequent actions.

### CASH instruction body

```json
{
  "instructionReference": "BORROWER-CASH-001",
  "amount": 10500.00,
  "currency": "INR",
  "collateralType": "CASH"
}
```

The amount above is illustrative only; display and submit the actual contract
requirement returned by the service.

### Return body

```json
{
  "instructionReference": "BORROWER-RETURN-001",
  "recallReference": null,
  "quantity": 100
}
```

Use the contract's outstanding quantity and the actual recall reference when
applicable. The server remains authoritative for eligibility.

### Fee payment body

```json
{
  "amount": 25.00,
  "currency": "INR",
  "allocations": [
    {
      "calculationResultReference": "server-returned-reference",
      "itemKey": "LENDING_FEE:2026-11-12",
      "amount": 25.00
    }
  ]
}
```

Values shown are examples of shape, not a payment instruction. Construct
allocations from the charge response and use a stable, unique payment
reference in the `Idempotency-Key` header.

## Borrower pages, in recommended build order

1. **Dashboard** — show the authenticated Borrower's requests, contracts,
   pending actions, and service status.
2. **Eligible agreements** — load eligible MSLAs; show their actual
   collateral, currency, coverage, and security terms.
3. **New loan request** — collect request fields and submit with an
   idempotency key. Borrowers may request a Locate first, then attach an
   approved, matching reference to a loan request.
4. **My requests and request detail** — paginated request list, status,
   rejection reason, and refresh. Never infer approval from HTTP success.
5. **Collateral inquiry and reports** — submit an inquiry for an owned request,
   display pending/received status, and render returned CASH/currency/haircut/
   coverage details read-only.
6. **Contract overview** — open the server-provided contract reference and
   snapshot after approval; show outstanding quantity, terms, and status.
7. **CASH collateral** — submit an instruction and show its lifecycle.
8. **Settlement progress** — show collateral and securities instructions,
   confirmations, timestamps, and current business status. Borrower has no
   simulated settlement-confirmation action.
9. **Margin calls and top-ups** — show calls, outstanding requirements, and
   submit eligible CASH top-ups.
10. **Returns** — request partial/full securities returns and monitor them.
11. **Fees and payments** — inspect charges and payment history; submit
    payments with explicit confirmation and idempotency.
12. **Collateral release** — request final release and show Custodian's
    follow-up status.
13. **Closed contract view** — read-only lifecycle history after Custodian
    closes the contract.

Pages 4–13 should be reachable only for resources owned by the authenticated
Borrower. Backend authorization is authoritative even when the UI hides
another party's references.

## API map

| Use | Method and path |
|---|---|
| Service status | `GET /api/foundation/status` |
| Eligible MSLAs | `GET /api/loan-requests/eligible-mslas` |
| Submit/list/get request | `POST /api/loan-requests`; `GET /api/loan-requests`; `GET /api/loan-requests/{requestReference}` |
| Contract view | `GET /api/loan-requests/{requestReference}/contract` |
| Collateral inquiry/report | `POST /api/loan-requests/{requestReference}/collateral-inquiries`; `GET /api/loan-requests/{requestReference}/collateral-reports` |
| CASH instruction | `POST /api/loan-requests/{requestReference}/collateral/cash-instructions` |
| Settlement progress | `GET /api/contracts/{transactionReference}/settlement` |
| Margin call/top-ups | `GET /api/contracts/{transactionReference}/margin-calls/{marginCallReference}`; append `/top-ups` for top-up list/submit |
| Returns | `GET` and `POST /api/contracts/{transactionReference}/returns` |
| Charges/payments | `GET /api/contracts/{transactionReference}/charges`; `GET` and `POST /api/contracts/{transactionReference}/payments` |
| Collateral release | `GET` and `POST /api/contracts/{transactionReference}/collateral-releases` |

## Authentication and interaction rules

- Every protected request uses the supplied JWT in the `Authorization` header.
- Borrower APIs require a valid Borrower token with the `borrower-service`
  audience and Borrower authorization (normally `ROLE_TRADER`).
- The Borrower forwards this token unchanged for eligible-MSLA lookups to
  Custodian. It must also be accepted by Custodian: use the same trusted
  signing key and issuer configured by both services, and include the
  `custodian-service` audience as well as `borrower-service`. Do not replace
  the token or add role claims in the browser.
- The token subject and organization claim must identify the active,
  provisioned Borrower. Roles are read from the service's local assignments;
  the UI must not choose an organization or infer authorization from token
  role claims.
- There is no login/token-issuing endpoint in these services. Use the existing
  trusted authentication/bootstrap flow to obtain a token accepted by both
  services; never mint a token or store a signing secret in the browser.
- Send a unique `Idempotency-Key` for loan submissions and payment submissions.
- Disable duplicate form submissions while requests are in progress.
- Confirm irreversible actions before sending them.
- Treat `400`, `401`, `403`, `404`, `409`, `422`, and `503` distinctly. On
  `401`, renew/re-authenticate through the trusted flow; `403` means the
  authenticated identity is not authorized for that action or the downstream
  Custodian denied access. On conflicts, refresh the resource rather than
  silently retrying.
- Display exact server-returned statuses, references, timestamps, and amounts.

## Current gaps to plan around

- Locate approval is an availability confirmation only; it does not reserve
  lender inventory. Inventory is checked and reserved only when the Custodian
  approves the linked loan request. An approved Locate remains valid until used.
  If a loan attempt is rejected, the Locate remains available for a retry; only
  one approved loan can consume it.
- Settlement confirmation endpoints are Custodian simulation controls; they
  are not Borrower actions and should not be exposed in the Borrower portal.
- Closing is Custodian-owned; Borrower requests return/release and monitors
  completion but does not directly close a contract.

## Backend source references

- Borrower routes: `backend/borrower_service/src/main/java/com/hexaware/borrower_service/controller/`
- Borrower request DTOs: `backend/borrower_service/src/main/java/com/hexaware/borrower_service/dto/request/`
- Existing endpoint/frontend context: `db/CLAUDE_FRONTEND_BACKEND_CONTEXT.md`
