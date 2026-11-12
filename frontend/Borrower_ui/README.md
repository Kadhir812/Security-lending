# SEC LEND — Borrower Operations UI v0.2

Standalone, **Borrower-only** React + Vite frontend styled to match the Custodian Securities Lending Command Center. All HTTP requests use **Axios** and the supplied `Security-lending-fixedapi.zip` Borrower Java controllers / DTOs, without making changes to the backend.

## Run

Requirements: Node 20.19+ (22 recommended), Java Borrower Service at `http://localhost:8082`, Custodian Service at `http://localhost:8081`, Security & Market at `http://localhost:8080`, MySQL, Kafka, and the backend's existing provisioned identities.

```bash
cd Borrower_ui
cp .env.example .env        # Windows: copy .env.example .env
npm install
npm run dev
```

Open `http://localhost:5174` (Vite may choose another port if it is occupied). When installed inside the supplied repo, use `cd frontend/Borrower_ui` instead. For production use `npm run build`; deploy `dist/` with an appropriate same-origin `/api` reverse proxy.

**Sign in:** Use the Borrower portal's sign-in page to authenticate with your email and password, or create a demo account. The Borrower service issues a short-lived JWT with the fixed `ROLE_TRADER` authority and the account's `organization_reference`; the browser stores it in `sessionStorage` and switches to **Live**. For eligible MSLAs, locates and loan submission through the Custodian dependency, the service-side token must include both `borrower-service` and `custodian-service` audiences, as specified in `docs/STAGE1_SETUP.md` from the backend project. The frontend never issues JWTs or stores signing secrets. Optional `VITE_BORROWER_JWT` remains available for trusted local testing; don't commit credentials.

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
| Locates | `GET/POST /api/locates`, `GET /api/locates/{reference}` |
| Requests | `GET/POST /api/loan-requests`, `GET /api/loan-requests/{reference}` |
| Eligible MSLAs | `GET /api/loan-requests/eligible-mslas` |
| Contract by request | `GET /api/loan-requests/{reference}/contract` |
| Contract portfolio | `GET /api/contracts` |
| Initial CASH collateral | `POST /api/loan-requests/{reference}/collateral/cash-instructions` |
| Settlement | `GET /api/contracts/{transactionReference}/settlement` |
| Margin calls | `GET /api/contracts/{transactionReference}/margin-calls`, `GET /api/contracts/{transactionReference}/margin-calls/{marginCallReference}` |
| Top-ups | `GET/POST /api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups` |
| Returns and recalls projection | `GET/POST /api/contracts/{transactionReference}/returns` |
| Charges and payments | `GET /api/contracts/{transactionReference}/charges`, `GET/POST /api/contracts/{transactionReference}/payments` |
| Final collateral release | `GET/POST /api/contracts/{transactionReference}/collateral-releases` |
| Health | `GET /api/foundation/status` (Bearer required) |

**Important API revision discrepancy:** `securities_lending_full_api_audit.md` was written for an older ZIP and lists REST `POST .../collateral-inquiries` and `GET .../collateral-reports`. They are **not registered** in the `LoanRequestController.java` inside the uploaded `Security-lending-fixedapi.zip`. Conversely, that updated ZIP **does** include newer `/api/locates`, `/api/contracts` and per-contract margin-call list GET routes. The frontend follows the **updated code**. Collateral BB/BA appears as an explicit API gap, not a fake working button.

**Other missing aggregates:** no per-security portfolio exposure trend, consolidated Borrower credit/utilization, realtime market quotes, FIX journal feed, alert stream, or tamper-evident unified activity endpoint. These display Demo badges or unavailable states. The Activity screen is clearly labelled as a recent-timestamp view assembled from actual request/contract projections and is **not** a backend audit log. Custodian-only approval, settlement confirmations, MTM, contract closure and release approval are intentionally excluded.

## Repository layout

`src/api/` (single Axios client + resource modules and DTO JSDoc), `src/pages/` (all operational screens), `src/components/` (design primitives, confirmation and validation), `src/layouts/`, `src/hooks/`, `src/mock/` (isolated Overview fixtures), `src/styles/` (standalone CSS), and `src/utils/` (INR, number, timestamp formatting).

See `STYLEGUIDE.md`, `API_COVERAGE.md`, and `VALIDATION.md` for design rules, mapping and actual test status.
