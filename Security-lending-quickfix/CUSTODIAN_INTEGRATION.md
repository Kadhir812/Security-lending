# Custodian UI + Backend integration

This archive is the **complete original project**, with its `frontend/Custodian_ui` scaffold replaced by the updated React/Vite Custodian command center. `frontend/Borrower_ui` and all original Java backend services, schema, migration, setup scripts, DTOs, controllers, and documentation are preserved.

## Start the original backend

Follow `docs/STAGE1_SETUP.md` and `db/CUSTODIAN_FRONTEND_WORKFLOW.md` for database/JWT/Kafka prerequisites. Required services: Security Master :8080, Custodian :8081, Borrower :8082, Valuation & Accrual :8083, and Kafka :9092. Custodian REST routes are implemented by the original backend (not by Vite).

## Run the integrated Custodian app

```bash
cd frontend/Custodian_ui
npm install
npm run dev
```

Open http://localhost:5173. The **development proxy** forwards browser calls to `http://localhost:8081` by default (including Bearer JWT, `X-Correlation-ID` and `Idempotency-Key`). Use **Session & Token Access** to paste a valid previously issued Custodian JWT: the backend intentionally has no login endpoint. Saving a token automatically switches the dashboard to Live mode.

## Verification boundaries

Live mode does not fabricate missing portfolios, historical exposure, FIX/audit feeds, BB/BA messages or price data. Their endpoints do not exist in the supplied backend. Other APIs are called from `src/api/client.js` via per-resource modules and use the exact request DTO field names. Simulated settlement/payment operations require explicit enablement and confirmation; do not use them as production settlement attestations.

## Quality / prerequisites

See `frontend/Custodian_ui/VALIDATION.md` for test results. A working local Maven/Java/DB/Kafka environment and an authorized Custodian token are required for end-to-end API smoke tests. Do not put production JWTs or signing keys in the project.
