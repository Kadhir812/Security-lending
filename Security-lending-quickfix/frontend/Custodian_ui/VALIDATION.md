# Custodian UI Integration & QA Report — 2026-11-11

## Delivered
- Original `Security-lending-quickfix` repository, preserving original Java services and business documentation, with a React+Vite Custodian UI replacing the previous bare scaffold.
- Original Borrower scaffold remains untouched.
- Local same-origin Vite API proxy to `http://localhost:8081` for Custodian routes. Backend Java source is not modified.

## Automated checks performed
- **PASS**: TypeScript 5.8.3 JavaScript/JSX parser on all **29** frontend `.js` / `.jsx` files (0 syntax errors).
- **PASS**: All local relative JS/JSX imports resolve.
- **PASS**: Five standalone CSS files parse using tinycss2 (0 top-level parser errors).
- **PASS**: All original Java backend files preserved byte-identically.
- **PASS**: Controller route and DTO names reviewed for Custodian setup, requests, settlement, margin, recalls, payments, release approval, close, and foundation status. Per-resource clients reuse one authenticated request client.
- **PASS**: No Tailwind, CSS-in-JS or JSX `style=` attributes introduced.
- **PASS**: Demo badges are in dashboard heading rows, not over table entries. The compact table action cells preserve native table alignment. The header, lifecycle, and fixed service-status dock have responsive CSS refinements.

## Tests blocked in this environment
- **NOT VERIFIED**: `npm install` — the NPM registry DNS lookup returns `EAI_AGAIN` for `registry.npmjs.org`; packages cannot download.
- **NOT VERIFIED**: `npm run build` — `vite: not found` because dependency installation could not complete.
- **NOT VERIFIED**: rendered browser comparison — local Chromium navigation reports `ERR_BLOCKED_BY_ADMINISTRATOR` for both `file://` and localhost URLs. Actual visual and mobile testing should be done in a normal browser after dependencies are installed.
- **NOT VERIFIED**: real backend integration test — no running MySQL/Kafka/other services or authorized JWT were provided, so network mutations/end-to-end Kafka processing were not exercised.
- **NOT VERIFIED**: Maven backend build, which requires Maven distribution, dependency downloads and service configuration unavailable here.

## Functional architecture notes
- The current Java backend exposes **no login endpoint**. JWT comes from the trusted existing issuer/bootstrap flow and is stored in sessionStorage when pasted.
- No general contract list, exposure trend, portfolio aggregation, FIX event read stream, unified audit feed, BB/BA inbox, or official price feed exist. Dashboard mock widgets carry provenance labels; Live mode does not fabricate data.
- Cash/return/release settlement confirmations and local fee payment confirm/fail are **simulations**, not external attestations. They are guarded by confirmation dialogs and an explicit enable switch.
- Requests and setup list APIs use service PageResponse pagination where applicable; updates use version fields.

## Local acceptance test procedure
1. Start the original backend services using `docs/STAGE1_SETUP.md`, including MySQL, Kafka and required local service dependencies.
2. Run `cd frontend/Custodian_ui && npm install && npm run build && npm run dev`.
3. Open http://localhost:5173; paste a valid Custodian JWT at `/auth`. Saving it switches to Live mode.
4. Confirm `GET /api/foundation/status` succeeds through the Vite proxy. Confirm the server-returned `X-Correlation-ID` appears in the bottom dock.
5. Verify pending queue GET, review GET, authorized approval/rejection confirmation, and rejected/expired-token errors.
6. Review actual contract settlement, margin, recall, fee payment, release approval, and closure states with server-issued transaction references. Use simulation only in the designated local demo setup.
7. Compare `/` at 1672×941 against `design-reference/dashboard-target.png`; test 1366px, 1024px, 768px and 390px widths.
