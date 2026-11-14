# SEC LEND — Borrower Operations UI v0.2

Standalone, **Borrower-only** React + Vite frontend styled to match the Custodian Securities Lending Command Center. All HTTP requests use **Axios** and the Borrower Java controllers / DTOs.

## Run

Requirements: Node 20.19+ (22 recommended), Java Borrower Service at `http://localhost:8082`, Custodian Service at `http://localhost:8081`, Security & Market at `http://localhost:8080`, MySQL, Kafka, and the backend's existing provisioned identities.

```bash
cd Borrower_ui
cp .env.example .env        # Windows: copy .env.example .env
npm install
npm run dev
```

Open `http://localhost:5174` (Vite may choose another port if it is occupied). When installed inside the supplied repo, use `cd frontend/Borrower_ui` instead. For production use `npm run build`; deploy `dist/` with an appropriate same-origin `/api` reverse proxy.

**Sign in:** Use the Borrower portal's sign-in page to authenticate with your email and password, or create an account through `POST /api/auth/signup`. The Borrower service issues a 24-hour JWT with the fixed `ROLE_TRADER` authority and the account's `organization_reference`; by default it includes both `borrower-service` and `custodian-service` audiences for the scoped eligible-MSLA lookup. The browser stores it in `sessionStorage` and switches to **Live**. Configure `BORROWER_TOKEN_AUDIENCES` when the service audience names differ from those defaults. The frontend never issues JWTs or stores signing secrets. Optional `VITE_BORROWER_JWT` remains available for trusted local testing; don't commit credentials.

## Link a Borrower identity to Custodian

1. Sign in to Borrower, open **Operations → Link with Custodian**, and generate a code. Send it securely to your Custodian operator; it expires in 24 hours and works once.
2. The operator signs in to Custodian, opens **Organizations**, pastes the code into **Link Borrower identity**, and selects **Link Borrower**.
3. Custodian creates the matching Borrower organization and grants that identity `TRADER` access only. The operator then creates an active lender mandate and MSLA through the existing setup pages.
4. Sign in to Borrower again (or refresh the eligible-MSLAs page) and submit a request using an eligible MSLA.

The link does not create an MSLA or authorize a loan; no administrator role is needed. Existing databases must apply `db/migrations/005_borrower_identity_link.sql`; fresh databases already include the table in the Custodian schema script.

## API and Axios integration

- `VITE_BORROWER_API_URL` defaults to `/api`, with same-origin Vite proxy to `http://localhost:8082`; this is intentional because the Java services do not expose browser CORS settings by default. For direct requests to `http://localhost:8082/api`, enable authorized backend CORS or a same-origin production reverse proxy.
- Only `src/api/client.js` imports Axios and creates `axios.create()`. Public sign-up and login requests omit `Authorization`; protected requests attach `Bearer <JWT>` and a unique `X-Correlation-ID`. The **returned** ID is displayed in the global footer. No automatic mutation retries.
- `Idempotency-Key` is mandatory and sent for loan submission and fee payment; loan key is preserved during an in-flight submission, payment key equals `paymentReference`.
- Errors distinguish 400 invalid, 401 missing/expired JWT, 403 role/organization denial, 404 missing/scoped record, 409 conflict, 422 business validation, 503 upstream failure, plus connectivity errors. Buttons are disabled during the operation.
- Read operations poll on explicit refresh and route changes, not by auto-resubmitting mutations; after asynchronous Kafka events, use Refresh to inspect the latest state.
- Operational pages never invent records. Demo fixtures live only in `src/mock/dashboard.js` and the Overview in Demo mode. **Live** mode queries real endpoints. Other screens require Live before reading or submitting. Distinguish SUBMITTED from PAID/CONFIRMED/SETTLED.

## Live Borrower workflows

| Screen | Borrower endpoint(s) |
|---|---|
| Locates | `POST /api/locates`, `GET /api/locates`, `GET /api/locates/{locateReference}` |
| Requests | `GET/POST /api/loan-requests`, `GET /api/loan-requests/{reference}` |
| Eligible MSLAs | `GET /api/loan-requests/eligible-mslas` |
| Custodian identity link code | `POST /api/auth/custodian-link-token` |
| Collateral inquiry / reports | `POST /api/loan-requests/{reference}/collateral-inquiries`, `GET /api/loan-requests/{reference}/collateral-reports` |
| Contract by request | `GET /api/loan-requests/{reference}/contract` |
| Contract portfolio | No list endpoint; approved requests resolve one contract at a time through the request-scoped route |
| Initial CASH collateral | `POST /api/loan-requests/{reference}/collateral/cash-instructions` |
| Settlement | `GET /api/contracts/{transactionReference}/settlement` |
| Margin call details | `GET /api/contracts/{transactionReference}/margin-calls/{marginCallReference}`; no contract-wide list endpoint |
| Top-ups | `GET/POST /api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups` |
| Returns and recalls projection | `GET/POST /api/contracts/{transactionReference}/returns` |
| Charges and payments | `GET /api/contracts/{transactionReference}/charges`, `GET/POST /api/contracts/{transactionReference}/payments` |
| Final collateral release | `GET/POST /api/contracts/{transactionReference}/collateral-releases` |
| Health | `GET /api/foundation/status` (Bearer required) |

The controller source is authoritative: both BB/BA routes and Borrower Locate routes are available, while a general contract-list route and a contract-wide margin-call list are not. Locate approvals confirm availability only; they do not reserve inventory and do not expire. Loan approval performs the authoritative inventory check/reservation and consumes its linked Locate; if a loan attempt is rejected, the Locate can be reused. Inquiry receipts mean only that the request was queued; they are not reports or proof of cash settlement. The frontend does not call unsupported routes.

**Other missing aggregates:** no per-security portfolio exposure trend, consolidated Borrower credit/utilization, realtime market quotes, FIX journal feed, alert stream, contract list or tamper-evident unified activity endpoint. These display Demo badges or unavailable states. The Activity screen is clearly labelled as a recent-timestamp view assembled from actual request projections and is **not** a backend audit log. Custodian-only approval, settlement confirmations, MTM, contract closure and release approval are intentionally excluded.

## Repository layout

`src/api/` (single Axios client + resource modules and DTO JSDoc), `src/pages/` (all operational screens), `src/components/` (design primitives, confirmation and validation), `src/layouts/`, `src/hooks/`, `src/mock/` (isolated Overview fixtures), `src/styles/` (standalone CSS), and `src/utils/` (INR, number, timestamp formatting).

See `STYLEGUIDE.md`, `API_COVERAGE.md`, and `VALIDATION.md` for design rules, mapping and actual test status.
