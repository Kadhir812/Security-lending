# Borrower workspace

This frontend provides the Borrower-specific sign-up, login and protected
account dashboard. It does not expose Custodian pages or let users choose
authorization roles.

## Run locally

```powershell
cd frontend\Borrower_ui
npm install
npm run dev
```

Open http://localhost:5174. The Vite development proxy forwards `/api` requests
to the Borrower service on port 8082. Set `VITE_BORROWER_API_URL` only when the
API is hosted elsewhere. A shared Axios client adds the tab's bearer token and
correlation ID to protected API requests.

## Demo authentication

The Borrower service must be running with its database migrated and a shared
JWT signing secret configured. Sign-up and login are available from the UI; no
environment flag or pre-issued token is required. Existing databases
created from the older schema also need the one-time migration
`db/migrations/005_demo_password_auth.sql`, followed by
`db/migrations/006_remove_administrator_role.sql`.

Sign-up collects a name, organization name, email and password (minimum 12
characters), and the backend assigns the fixed `TRADER` role. It cannot grant
Custodian or Administrator access. Passwords are BCrypt-hashed in the Borrower
database. The service issues a 15-minute Borrower JWT after sign-up or login;
the browser stores it in tab-scoped `sessionStorage` and requires sign-in again
after expiration.

Anyone who can reach the service can sign up. This is a demo flow, not a
production identity system; use a trusted identity provider or controlled
account provisioning for real deployments. Do not put signing secrets in
frontend configuration.
