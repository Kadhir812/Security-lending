# Complete securities-lending project — Custodian integration

The entire original backend is under `backend/`; the new Custodian UI is under `frontend/Custodian_ui/`; the Borrower scaffold is under `frontend/Borrower_ui/`.

**First**, follow `docs/STAGE1_SETUP.md` to configure MySQL, the shared JWT issuer, Kafka and related services. Start the Custodian service on port `8081` and its dependencies (8080, 8082, 8083, Kafka 9092). The backend services intentionally do not expose a browser login endpoint.

**Then**, from this repository root:

```bash
cd frontend/Custodian_ui
npm install
npm run dev
```

Open `http://localhost:5173`. Go to **Session & Token Access**, paste a pre-issued Custodian token and open Live mode. In development, Vite forwards `/api` to the Custodian service on port 8081. Switch to Demo for the visual-only dashboard widgets. The original backend files are preserved, not replaced or emulated.

See `CUSTODIAN_INTEGRATION.md`, `frontend/Custodian_ui/README.md`, `frontend/Custodian_ui/STYLEGUIDE.md`, and `frontend/Custodian_ui/VALIDATION.md` for configuration, API gaps and test limitations.
