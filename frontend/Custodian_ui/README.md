# SEC LEND v0.2 — Custodian Operations Frontend

**Custodian-only** React 19 + Vite frontend integrated into the supplied securities-lending repository. The combined project ZIP also contains the unmodified backend services. The screenshot-focused dashboard uses explicit illustrative data only where REST read endpoints are missing. Other pages perform live authorized REST operations and never invent authoritative valuations, statuses or references.

## Run locally

```bash
cd Custodian_ui
npm install
cp .env.example .env   # optional; set ONLY the API base URL / non-secret environment options
npm run dev
```

Open **http://localhost:5173**. To build a production bundle:

```bash
npm run build
npm run preview
```

The default public Custodian API address is `http://localhost:8081` via `VITE_CUSTODIAN_API_URL`. **In Vite development, the default localhost:8081 address automatically routes through the same-origin `/api` proxy to port 8081**, avoiding browser CORS failures. For other API hosts, set `VITE_CUSTODIAN_API_URL` explicitly and configure the backend reverse-proxy/CORS policy. Backend, MySQL, Kafka and the dependent services must already be running. When accessing the API directly at `8081`, ensure the backend CORS policy allows the development origin and the `Authorization`, `Content-Type`, `X-Correlation-ID` and `Idempotency-Key` request headers, plus exposes the response `X-Correlation-ID` header.

### Authentication & access

Use the **Sign in** or **Create an account** form at `/auth`. Login calls
`POST /api/auth/login`; sign-up calls `POST /api/auth/signup` directly. For a local demo, apply
`db/migrations/005_demo_password_auth.sql` and
`db/migrations/006_remove_administrator_role.sql` to existing databases. New accounts are assigned the fixed
`CUSTODIAN_OPERATOR` role. Passwords are stored as BCrypt hashes in the
Custodian database.

Successful login returns a Custodian-audience JWT that is retained in
tab-scoped `sessionStorage`; the UI clears it after its 24-hour expiry. The
backend still validates the token and local role for every protected request.
Never put JWT signing keys or real tokens in the browser or source control.
Anyone who can reach the service can sign up; use a trusted identity provider
or controlled provisioning for real deployments.

Each request carries `Authorization: Bearer ...` (when configured) and a unique `X-Correlation-ID`; the service's returned correlation ID appears in the bottom status bar. The status bar also shows the service status (`GET /api/foundation/status`), configured environment and **Live / Demo** mode.

`400`, `401`, `403`, `404`, `409`, `422` and `503` return distinct human-readable errors. `401` clears a pasted session token. `409` does not auto-retry and prompts a resource refresh. Mutation buttons disable during their in-flight execution. Approve/reject, payment processing, settlement simulations, collateral-release approval and contract closure require explicit confirmation. Rejection/failure require reasons. MTM uses an `Idempotency-Key`. All lists using `PageResponse` use the returned zero-based `page`, `size`, `totalElements` and `totalPages`.

### Link a Borrower identity

After the Borrower generates a one-time code in **Borrower → Operations → Link with Custodian**, sign in as a Custodian operator and open **Organizations → Link Borrower identity**. Paste and submit the code within 24 hours. This creates the Borrower organization and its matching user with `TRADER` access only; it does not create an administrator or an MSLA. Next create the active lender mandate and MSLA using the existing **Mandates** and **MSLAs** pages. Existing databases must apply `db/migrations/005_borrower_identity_link.sql`; fresh schema scripts include the table.

## Available Custodian pages

| Section | Routed screens |
|---|---|
| Overview | `/` command center |
| Requests | `/requests` pending queue, `/requests/review/:reference` review & approve/reject, `/locate-requests` Locate review |
| Master data | `/organizations`, `/mandates`, `/inventory` |
| Agreements and controls | `/mslas`, `/credit-limits` (plus history), `/haircuts`, `/lending-fees` |
| Contract portfolio | `/contracts` (manual live lookup; portfolio aggregate is demo-only) |
| Operations | `/settlement`, `/margin`, `/recalls`, `/payments`, `/release-queue`, `/close` |
| Oversight | `/audit`, `/collateral-inquiries`, `/auth` (includes glossary) |

Each resource screen has KPI strip, paginated read table or agreement-scoped read list, DTO-based create/edit form, operator rules, FIX/workflow context and footer disclaimer. Update bodies use server-returned optimistic concurrency `version`. MSLA `eligibleSecurities.allowed_types` follows the Java `@JsonProperty` mapping; credit-approval and collateral calculations remain backend-owned. Contract actions are scoped by **real** `transactionReference` supplied by a service decision and accessible through links or direct lookup.

### Simulated settlement is not real settlement

Simulation controls are **disabled by default**. On `/settlement?transaction=...`, an operator must explicitly tick the **SIMULATION – demo only** enable checkbox, then review a confirmation dialog and enter real returned instruction and confirmation references (or failure reasons). The supported local `/simulated-confirm` and `/simulated-fail` endpoints operate only through the backend and must never be represented as production clearing evidence.

### Demo-only widgets and API gaps

The orange-on-black visual design is based on the supplied dashboard PNG. **Demo data – API pending** badges mark screenshot-matched visuals. Switch the global display to **Live** to hide synthetic values and show honest unavailable/empty states.

- **Demo-only, no currently supported aggregate endpoint:** exposure trend, exposure treemap, utilization/credit/collateral *portfolio totals*, active-contract **portfolio list**, active-contract counts, FIX message stream, market ticker/market-open pill, operational alerts, unified cross-service activity feed, and illustrative dashboard contract lifecycle/event.
- **Dashboard incoming requests:** sample rows in Demo; actual paginated `GET /api/loan-requests` in Live. No demo row can be approved/rejected on the server.
- **Live with limited read scope:** contract settlement, margin, recall, fees and payment operations require an actual transaction reference because `GET /api/contracts` does not exist. Per-inventory-row available, reserved, on-loan values are read live, not mock-derived.
- **Unsupported read integration:** Custodian BB collateral inquiries and BA reports have no REST inbox/report endpoint. The visibility page explains this gap; it never queries Kafka/database directly.
- **Locate review** is available at `/locate-requests`. Approval checks current lender availability without reserving it; the Locate has no expiry. A later loan approval rechecks and reserves inventory.
- **FIX naming clarification:** the original business PDF uses AF for “Request For Security”; `db/FIX_MAPPING.md` states the current backend sends project-defined `35=U1` instead. The frontend displays the current backend behavior, with AF explained in the glossary.

A production read-API backlog should include full contract portfolio, time-series exposure, per-security exposure, aggregated risk alerts, FIX/audit event feeds, and authorized BB/BA history.

## Source structure

```text
src/
  api/        client.js, types.js and per-resource modules (all REST through one typed client)
  components/ reusable panels, status, tables, modals and lifecycle stepper
  hooks/      application/session/service status and resource data hooks
  layouts/    sidebar, ticker, search, user menu, routing tabs and status bar
  mock/       screenshot-only illustrative datasets
  pages/      dashboard, setup, requests, contracts and operations
  styles/     tokens.css, base.css, layout.css, components.css, pages.css
  utils/      INR, time and status display formatting
```

All styling is external CSS, with token variables and component descriptions in **STYLEGUIDE.md**. Google **Roboto** with local fallbacks is the sole UI typeface. No inline styles, CSS-in-JS or Tailwind.

## Backend contract references

- `db/CUSTODIAN_FRONTEND_WORKFLOW.md`
- `db/CLAUDE_FRONTEND_BACKEND_CONTEXT.md`
- `db/FIX_MAPPING.md`, `db/COMMUNICATION.md`, `db/BORROWER_FRONTEND_WORKFLOW.md`
- Custodian Java controllers / request and response DTOs, plus the supplied `Security Lending V0.2.pdf`.

The frontend intentionally excludes backend code, database SQL, signing secrets, Kafka operations and server-owned business calculations.
