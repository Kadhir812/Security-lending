# Stage 3D: borrower loan requests

## Changed surfaces
- Borrower APIs: eligible MSLA lookup; idempotent create-and-submit; scoped list/get.
- Custodian API: borrower-scoped eligible MSLA lookup using local `TRADER` membership.
- Custodian APIs: list/detail of received requests, scoped through authenticated Custodian → mandate → MSLA.
- Borrower and Custodian: service-local loan-request records; Custodian Kafka handler validates and stores receipt.
- Existing outbox/inbox/retry paths are reused; `QUEUED` → `SENT`/`FAILED` tracks broker delivery, while business status remains `PENDING`.
- Added `settlement_currency` as nullable local request data; existing rows remain `NULL`.
- No new tables were added in 3D: the supplied schema then contained 48 physical tables; existing service-owned request and messaging tables were used.
- No inventory/credit reservation, approval, contract, settlement, or FIX mapping was added.

## API example
`GET /api/loan-requests/eligible-mslas` returns only active/effective agreements for the authenticated borrower.

```http
POST /api/loan-requests
Authorization: Bearer <borrower JWT>
Idempotency-Key: LR-2026-000042
Content-Type: application/json

{"mslaReference":"MSLA-2026-001","securityIsin":"INE002A01018","quantity":1000,"requestedSettlementDate":"2026-11-10","settlementCurrency":"INR","locateReference":null}
```

The existing schema has no draft status, so POST atomically creates the request and its outbox event. The idempotency key becomes `requestReference`; an identical replay returns the same request, while a changed payload conflicts. There is no edit endpoint.

Custodian receipt reads require an active local `CUSTODIAN_OPERATOR`:

```http
GET /api/loan-requests?page=0&size=20
GET /api/loan-requests/LR-2026-000042
```

Each query scopes through local MSLA → mandate ownership; a request outside the operator's Custodian scope is returned as not found.

## Event example
See [loan-request-submitted.json](../contracts/events/examples/loan-request-submitted.json) and [payload schema](../contracts/events/loan-request-submitted-v1.schema.json). The envelope uses `sec-lending-requests`; Custodian validates routing, payload version, borrower membership, MSLA/mandate parties and dates, security status/type, currency, and optional locate consistency before persisting.

## Local Kafka
Run once from project root: `.\scripts\setup-kafka.ps1`; start with `.\scripts\kafka.ps1 -Action Start`. Create request and DLT topics before enabling consumers:

```powershell
.\kafka\bin\windows\kafka-topics.bat --bootstrap-server localhost:9092 --create --if-not-exists --topic sec-lending-requests --partitions 3 --replication-factor 1
.\kafka\bin\windows\kafka-topics.bat --bootstrap-server localhost:9092 --create --if-not-exists --topic sec-lending-requests.DLT --partitions 3 --replication-factor 1
```

Set `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`; enable `KAFKA_PUBLISHER_ENABLED=true` for Borrower and `KAFKA_CONSUMER_ENABLED=true` for Custodian. DLT publication must be available; failed DLT sends do not acknowledge the source offset.

## Configuration and blockers
- Borrower forwards the authenticated JWT to Custodian; configure its audiences to include both `borrower-service` and `custodian-service`, and provision the same borrower identity with local `TRADER` membership in Custodian. No service credentials are used.
- Set `CUSTODIAN_SERVICE_BASE_URL` and `SECURITY_MARKET_BASE_URL`; configure service-owned databases and trusted JWT issuer/audiences as well.
- No approved FIX mapping exists for initial loan-request submission; this phase sends the existing v1 JSON envelope only, not QuickFIX. Do not assign a FIX type until an approved mapping is supplied.
- Settlement currency is validated against the MSLA currency. Whether security denomination must also match is a business-rule question; the current flow does not impose that additional restriction.
- Broker acknowledgement marks `SENT`; there is no receiver acknowledgement event yet, so it does not prove Custodian persistence. Business approval remains separate and unimplemented.
- Availability is not reserved; it must be rechecked atomically by the later approval workflow.
- Phase 3E adds the Borrower-owned `loan_contract_view`; the current combined schema has 49 tables.

## Verification
Targeted Maven package build passed; 16 request-flow tests and 10 Custodian authorization tests passed (zero failures/errors/skips). Local Kafka startup, request/DLT topic creation and a real broker producer/consumer smoke check passed; Kafka was stopped afterward. Full Borrower REST → outbox → Kafka → Custodian inbox/request → Custodian REST E2E is blocked: no disposable `STAGE1_MYSQL_URL` or configured service databases were provided. Existing message-component IT covers retry/recovery, DLT, duplicate delivery, wrong destination and rollback, but was not run and is not cross-service E2E evidence.
