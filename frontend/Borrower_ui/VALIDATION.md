# Borrower UI — validation record

**Scope:** Built against the provided updated Java controller and DTO sources. All frontend HTTP operations use a single Axios client (`src/api/client.js`). Axios is a dependency installed via `npm install`, not checked into the ZIP.

| Check | Outcome |
|---|---|
| Compare endpoint paths with `backend/borrower_service/.../controller/` | All 23 mapped GET/POST routes represented in resource modules |
| Compare form JSON with Java request DTOs | Exact body field names; header idempotency for loan/payment |
| TypeScript parser syntax check of all `.js` / `.jsx` | PASS — 23 checked; 0 syntax errors |
| Relative import target check | PASS — no unresolved relative imports |
| CSS parser syntax check | PASS — 6 standalone CSS files; 0 parse errors |
| `npm install` | **Blocked** — registry DNS/network unavailable in this execution environment |
| `npm run build` | **FAILED TO RUN** — `npm run build` exits 127: `sh: vite: not found` (npm dependency installation blocked) |
| Browser pixel comparison | **Not verifiable** without installed React, Vite and browser-loaded frontend |
| Live backend execution | **Not performed** — no running Java/DB/Kafka services and no user JWT |

This is not a claim that backend endpoints are working end-to-end; it is a source-level integration. In a network-enabled local environment, run:

```sh
cd Borrower_ui
npm install
npm run build
npm run dev
```

Then sign in or create a demo Borrower account, verify `/api/foundation/status`, and test locate → loan → contract → collateral → return/fees/release with a running backend.

See `API_COVERAGE.md` for source-grounded gaps from the older audit.
