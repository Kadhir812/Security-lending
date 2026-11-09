# Implementation progress

## Lifecycle verification (2026-11-09)

- Part 1 runtime/schema alignment: no active Spring profile; each service's
  datasource was queried using its own configured local MySQL credentials.
  Existing DBs were not reset and no records were inserted.
- Local schema was 49 tables because `custodian_db` lacked only
  `credit_exposure_history` (27 vs 28 in its authoritative fresh schema).
  Existing `margin_call.shortfall_amount` was already generated and the 3B
  audit columns, 3D currency fields, and 3E contract-view table were present.
  Applied only the missing table creation from
  `V3G__credit_exposure_and_satisfied_margin_call.sql` and the missing
  `loan_contract_view.version` column from
  `V3G__borrower_contract_view_version.sql`; both changes are additive.
  Final counts: 5+28+12+5 = 50; no destructive DDL or data reset.
- Market started from its Maven wrapper in a VS Code task terminal; port 8080
  `/actuator/health` returned UP and startup output is retained at
  `app-logs/market-startup.log`.
- Workspace Kafka still fails moving partition
  `sl3d3e-requests-20261108-0` with Windows `AccessDeniedException`. At retry,
  no workspace Kafka JVM or listener existed; the existing data `.lock` was
  left untouched. No Sysinternals `handle.exe` is available to identify any
  external Windows file-handle owner. Started the previously verified isolated
  broker using the same `localhost:9092`/`9093` endpoints; Kafka API versions
  request succeeded. Its data is retained under
  `%TEMP%\sl-lifecycle-kafka-20261109`.
- Custodian, Borrower, and Valuation are not started: required
  `JWT_SHARED_SECRET` is absent from process, user, and machine environments.
  Both Custodian and Borrower `users`, `roles`, and `user_roles` are empty; no
  seed script exists. Existing test fixtures require `CUSTODIAN_OPERATOR` and
  Borrower `TRADER` membership. Authorization was not weakened and no users
  were invented. Configure the secret and approved local principals before
  starting those services or running authenticated simulations.
- Messaging publisher/consumer flags remain disabled by default in service
  properties, so broker protocol connectivity is verified but service Kafka
  clients are not enabled. No business lifecycle was run.
- Reviewed approved 3H rules against 3J: fee days use next UTC midnight,
  include both delivery and return settlement dates, select a price at/before
  cutoff no older than 24 hours, use ACT_365, and round only the final daily
  amount HALF_UP to cents. No 3J mismatch found.
- Fixed the Borrower JWT-placeholder test with an explicit test-only property;
  production still requires `JWT_SHARED_SECRET`.
- Added focused tests for delivery/return-day quantities and fee HALF_UP
  calculation. `mvn -pl backend/borrower_service,backend/custodian_service,backend/valuation_accural -am test`:
  PASS, Borrower 29, Custodian 45, Valuation 14; 88 total, no failures/errors/skips.
- `python db/validate_structure.py`: PASS, 50-table ceiling. Fresh schemas
  imported into disposable databases on configured MySQL `localhost:3306`;
  existing application databases were not changed.
- Workspace Kafka data was preserved. Market is healthy; remaining service
  health and authenticated API checks are pending the JWT secret and principals.
- Health checks, authenticated API/Kafka lifecycle, settlement and fee
  reconciliation, concurrency, stale-event, and projection checks remain
  pending.

## Current implementation status (2026-11-09)

- 3J final CASH release and contract closure are implemented with existing
  `collateral`, `settlement_instruction`, `contract_status_history`, inbox, and
  outbox records. No table or migration was added; the 50-table ceiling is kept.
- Borrower APIs: `POST/GET /api/contracts/{ref}/collateral-releases`; request
  body `{"releaseReference":"REL-001"}`. Custodian queue:
  `GET /api/collateral-releases/requests`; approval:
  `POST /api/contracts/{ref}/collateral-releases/{releaseRef}/approve`.
- Custodian payout APIs:
  `POST /api/contracts/{ref}/settlement/collateral-releases/simulated-confirm`
  with `{"instructionReference":"R:REL-001:60","confirmationReference":"PAY-001"}`,
  or `/simulated-fail`. Approval creates pending payout instructions; only
  authorized simulated confirmation increments collateral `released_cash_amount`.
- Closure API:
  `POST /api/contracts/{ref}/close` with
  `{"closureReference":"CLOSE-001","reason":"Final obligations settled"}`.
  Closure requires fully returned securities, zero contract credit/reservation,
  no held collateral, no active recalls/margin calls, completed final accrual,
  paid fees, and no unresolved financial settlements. It records status history
  and emits one versioned closure event atomically.
- Events: Borrower `FINAL_COLLATERAL_RELEASE_REQUESTED` → Custodian inbox;
  Custodian `SETTLEMENT_INSTRUCTION_UPDATED` payout states → Borrower projection;
  `CONTRACT_CLOSED` updates the Borrower contract view. Reused outbox/inbox and
  settlement versioning provide retry and duplicate protection.
- Final accrual now uses the settled quantity through the return settlement
  date, per 3H’s date-inclusion rule. Release waits until that UTC date completes
  and required authoritative prices/accrual results are available.
- 3J verification: clean Borrower/Custodian build and all 8 focused tests passed
  (payout success/rejection/duplicate, closure success/duplicate, Borrower
  settlement projection/versioning and party isolation). Kafka/MySQL E2E,
  live auth, and schema-backed integration checks remain deferred.
- The prior Borrower JWT-placeholder assertion is fixed; see the lifecycle
  verification results above.
- Unresolved accounting policy: fee correction credits/refunds/netting are not
  automated; any outstanding credit balance blocks release/closure for manual
  resolution. Payout actions remain simulated.
- 3I recalls and securities returns are implemented using the existing
  `recall_request`, `settlement_instruction`, and `inventory_movement` tables;
  no new table or migration was added.
- Custodian APIs: `POST/GET /api/contracts/{ref}/recalls` and
  `GET /api/contracts/{ref}/recalls/{recallRef}`. Borrower APIs:
  `POST/GET /api/contracts/{ref}/returns`. A return body is
  `{"instructionReference":"RET-001","recallReference":"REC-001","quantity":40}`;
  omit `recallReference` for a voluntary return.
- Recall example: `POST /api/contracts/TX-123/recalls` with
  `{"recallReference":"REC-001","quantity":100,"dueAt":"2026-11-10T12:00:00Z","reason":"Return request"}`.
- Return events: Borrower `SECURITY_RETURN_SUBMITTED` →
  Custodian pending record and `SECURITY_RETURN_RECEIVED` →
  authorized simulated confirm/fail → `SETTLEMENT_INSTRUCTION_UPDATED`.
  Confirmation atomically updates contract quantities, generated available
  inventory, movement, credit exposure, recall progress, and outbox events.
  Valuation calculates exposure from the remaining quantity; pending returns do
  not change settled balances. Voluntary confirmed returns satisfy active recalls
  oldest-first to avoid counting the same returned quantity twice.
- Custodian confirmation examples:
  `POST /api/contracts/TX-123/settlement/returns/simulated-confirm` with
  `{"instructionReference":"RET-001","confirmationReference":"SIM-001"}`;
  failure uses `/simulated-fail` with a failure reason. Failed instructions can
  be retried; duplicate delivery and confirmation are idempotent.
- 3I verification: clean compile succeeded for Borrower, Custodian, and Valuation
  via `mvn -f D:\\yali\\sl\\Security-lending\\pom.xml -pl backend\\borrower_service,backend\\custodian_service,backend\\valuation_accural -am clean compile -DskipTests`.
  Focused delivery/rejection/duplicate tests passed (5 tests across Borrower and
  Custodian). Kafka/MySQL runtime, concurrent settlement, and E2E checks remain
  deferred. Only simulated settlement actions are implemented.
- Recall/return rule to confirm: an unlinked voluntary return is allocated to
  active recalls oldest-first. No automatic recall-deadline derivation, overdue/
  default processing, cancellation API, collateral release, or contract closure
  is added; the caller supplies dueAt because no recall calendar policy is defined.
- 3A–3E remain the historical baseline. The combined schema defines 50 physical
  tables (the project limit); current 3G adds no table beyond that limit.
- 3F implementation is complete: Borrower CASH instructions are delivered by
  Kafka, persisted as pending by Custodian, and confirmed only through the
  authorized simulated-settlement endpoint. Full initial CASH gates full
  securities delivery; delivery confirmation atomically converts inventory
  reservation and credit, records movement/history, and publishes the Borrower
  update. Borrower settlement outcomes and scoped progress APIs are wired.
- 3F changed files: Custodian `InitialSettlementService`,
  `InitialSettlementController`, `CollateralInstructionSubmittedHandler`,
  `InventoryMovement`/repository; Borrower `SettlementInstructionUpdatedHandler`,
  `CollateralInstructionReceivedHandler`, `SettlementProgressController`,
  `CollateralInstructionService`, and both service route configurations.
- 3F events: Borrower `COLLATERAL_INSTRUCTION_SUBMITTED` →
  Custodian `COLLATERAL_INSTRUCTION_RECEIVED` → final
  `SETTLEMENT_INSTRUCTION_UPDATED`. API examples:
  `GET /api/contracts/TX-123/settlement`,
  `POST /api/contracts/TX-123/settlement/cash-instructions/simulated-confirm`,
  `POST /api/contracts/TX-123/settlement/securities/simulated-confirm`.
- Confirmation body: `{"instructionReference":"CASH-001","confirmationReference":"SIM-001"}`.
- 3F verification: clean compilation of Borrower and Custodian succeeded with
  `mvn -f D:\yali\sl\Security-lending\pom.xml -pl backend\borrower_service,backend\custodian_service -am clean compile -DskipTests`.
  No tests were run per request. Kafka/MySQL end-to-end, broker retry/replay,
  database validation, and runtime authorization remain unverified.
- 3G implementation now includes Valuation-owned MTM calculations and persistent
  calculation records, Custodian manual/scheduled runs, exposure deltas, margin
  calls/overdue transitions, Borrower top-up submission/progress, and Custodian
  simulated receipt confirmation. Pending/failed cash does not increase collateral.
- 3G compile verification succeeded on 2026-11-09 with
  `mvn -q -f D:\yali\sl\Security-lending\pom.xml compile`. No tests were run,
  per request. Kafka/MySQL end-to-end, concurrency and runtime authorization
  behavior remain unverified.
- Open 3G behavior: automatic call resizing/closure beyond the existing satisfied
  state is undefined; no liquidation, penalties, or contract termination were added.
  3F runtime Kafka/MySQL and event replay checks remain pending.
- 3H lending-fee accrual and payment implementation is complete within the approved
  non-rebate scope. Confirmed rules: daily market value at next UTC midnight;
  include delivery/return settlement dates; use the latest price at/before cutoff,
  at most 24 hours old; ACT_365; no intermediate monetary rounding and final
  daily charge HALF_UP to cents. Only supported two-decimal currencies; no FX.
- Valuation calculates and persists immutable daily results in existing
  `calculation_run`/`calculation_result`. Custodian reconstructs settled quantity
  from settlement history, validates inputs before acceptance, posts transactional
  charge events, and records each payment allocation in existing
  `lending_fee_payment`. Borrower stores charge/payment projections in its existing
  contract snapshot. No new table or migration was added.
- Custodian APIs: `POST /api/contracts/{ref}/accruals`,
  `GET /api/contracts/{ref}/charges`, `GET /api/contracts/{ref}/payments`, and
  `POST /api/contracts/{ref}/payments/{paymentRef}/confirm|fail`. Borrower APIs:
  `GET /api/contracts/{ref}/charges|payments` and
  `POST /api/contracts/{ref}/payments` with required `Idempotency-Key`.
- Example accrual: `POST /api/contracts/TX-123/accruals?from=2026-11-07&to=2026-11-08`.
  Example Borrower body:
  `{"amount":"12.34","currency":"INR","allocations":[{"calculationResultReference":"<result-uuid>","itemKey":"LENDING_FEE:2026-11-07","amount":"12.34"}]}`.
  Custodian confirmation body: `{"externalConfirmationReference":"PAY-987"}`.
- Events: Custodian publishes `LENDING_FEE_CHARGE_UPDATED`; Borrower sends
  `FEE_PAYMENT_SUBMITTED`; Custodian emits `FEE_PAYMENT_RECEIVED` and then
  `FEE_PAYMENT_UPDATED` with `PAID` or `FAILED`. Pending/submitted payments reserve
  capacity; only confirmed `PAID` reduces the reported outstanding charge.
- Changed-file groups: Valuation fee calculator, DTOs, REST client/service/controller;
  Custodian accrual scheduler/controller, payment entity/repository/service/handler,
  settlement-history inputs and event configuration; Borrower payment DTO/controller,
  contract projection, inbox handlers and transaction locking; this progress entry.
- Verification: `mvn -DskipTests compile` succeeded for all four services on
  2026-11-09. No tests were run per request; runtime Kafka/MySQL, DB schema
  validation, and concurrent/authorization behavior remain unverified.
- Rebates remain unimplemented: `prompts/pr.md` explicitly says not to invent them,
  and only fee accrual rules were approved. Corrections can expose a credit balance;
  refunds/netting are not automated. The earlier 3F Borrower delivery-projection
  gap noted in this historical entry was addressed in the current status above;
  full Kafka/MySQL E2E remains open.

## Stage 1: common persistence and authentication foundation

Scope follows the supplied Stage 1 instructions, the subsequent request to use
Spring Boot 4, and the Lombok/style conventions added to prompt 00. Business
scenarios are deferred.

### Implemented

- Root Maven reactor with four independent executable Spring Boot 4.1.2 JARs on
  Java 21. Lombok is configured as an annotation processor and excluded from JARs.
- Service-specific database URLs and credentials, fixed owned database names,
  UTC DATETIME mapping, `ddl-auto=validate`, no automatic schema generation.
- Service-local `MessageLog`, `OutboxEvent`, `InboxMessage` and repositories in all
  four services. SQL column names, lengths, enum values, JSON, unique constraints,
  checks and processing indexes are represented without adding tables.
- Borrower/Custodian `User`, `Role`, `UserRole` and repositories. Custodian also
  has a minimal `Organization` mapping. Borrower retains its scalar external
  organization reference. Versioned entities use nullable Java versions before
  insertion, allowing JPA to assign the initial version correctly.
- RS256 JWT resource-server validation for Borrower, Custodian and Valuation:
  configured JWKS, trusted issuer, required expiry, not-before, audience, subject
  and organization claim. No token issuer, password storage or auth service.
- Borrower/Custodian resolve the exact local issuer/subject, reject inactive or
  unknown users, load only local roles, and compare the verified organization to
  local authorization data. Case-sensitive checks compensate for SQL collation.
- Organization guards and a local Custodian mandate authorization lookup. Mandates
  must be active, effective and owned by the authenticated Custodian organization.
- Valuation authenticates configured client/subject pairs and service identity,
  separates preview and authoritative scopes, and reserves authoritative scope
  for the configured Custodian. It has no local users/roles or cross-DB queries.
- Market application security explicitly permits unauthenticated access, with no
  JWT configuration or generated-password login. Actuator detail/other routes
  remain restricted. Other services remain protected.
- Correlation IDs, structured logs, UTC Clock, ProblemDetail exception translation
  and a minimal status DTO/controller. Authorization probe endpoints are test-only.
- Lombok `@Data`/`@Builder` for entity/response data, builder defaults, safe entity
  identity and ID-only toString; constructor injection with Lombok where suitable;
  separate enum files and formatted code with explicit imports.

### Implemented once, copied and adapted

Borrower is the source pattern for messaging persistence, resource-server token
validation, local user/role resolution, common configuration and verification.
Messaging entities/repositories and common infrastructure were copied to the
other three services with package changes. Custodian adapts the user organization
FK, organization status/type and mandate checks. Valuation adapts the JWT pattern
to configured service identities/scopes. Market keeps messaging persistence but
uses the explicit public-endpoint exception. There is no shared JPA module.

### Changed files

- Root `pom.xml`, `.gitignore`, `README.md`, and the four `backend/*/pom.xml` files.
- Four `application.properties` files and formatting of the existing application
  entry points.
- Each service: `entity/{MessageLog,OutboxEvent,InboxMessage}.java`, matching
  repositories and enum files; `config/{FoundationConfiguration,CorrelationIdFilter}`;
  `exception/ApiExceptionHandler`; `controller/FoundationController`;
  `dto/response/FoundationStatus`; `security/config/SecurityConfiguration`.
- Protected services: `security/config/JwtConfiguration` and
  `security/authentication/TokenValidation`.
- Borrower/Custodian: user/role/assignment entities and repositories,
  `LocalUserAuthentication`, `LocalUserJwtConverter`, `OrganizationAuthorization`.
- Custodian: `Organization`, its enums/repository, `MandateAccessRepository` and
  `MandateAuthorization`.
- Valuation: `ServiceJwtConverter` and `CalculationAuthorization`.
- Each service's application tests, `SecurityHttpTest`, `PersistenceIT`; Borrower
  and Custodian test Mockito configuration.
- `prompts/00_MASTER_INSTRUCTIONS.md` coding conventions, this progress document
  and `docs/STAGE1_SETUP.md`. Existing SQL definitions were not rewritten.

### Verification evidence

The full `mvn -Pmysql-it verify` run completed successfully on 2026-11-07
at 07:24 IST: all four modules built, with 33 ordinary/security tests and 21
real-MySQL integration tests passing (zero failures/errors/skips). MySQL 8.0.42
ran with workspace-local test data on port 13306. Reports are in each module's
`target/surefire-reports` and `target/failsafe-reports`; the original build log is
`.stage1/verify.log`. External identity-provider provisioning remains deployment
setup, not an automated-test claim.

### Boundaries and environment

The schema contains organization-level Custodian mandates, not individual
user-to-mandate grants. The implemented policy is organization ownership plus
operator role. No extra mapping table or broad administrator bypass was invented.

Real external identity-provider provisioning and live token acquisition remain
deployment setup. Development instructions and claim examples are in
`STAGE1_SETUP.md`; automated security tests generate transient RSA keys and send
signed tokens through the real decoder/filter chain.

Next stage only after a separate request: outbound service calls/OAuth client
wiring, Kafka publishers/consumers/retries, FIX adapters, financial calculators,
business schedulers and lending workflows. None are implemented here.

## Stage 2: common Kafka messaging — completed before Stage 3A

The common local outbox/inbox, delivery retry/lease fencing, audit, routing and
dead-letter infrastructure was implemented in all four services. No lending
business handlers were added. See `STAGE2_MESSAGING.md` for scope and operation.
Earlier real-MySQL runs passed 33 ordinary/security tests, 21 persistence checks
and 72 messaging component checks; the real-Kafka Borrower/Custodian round trip
also passed (127 distinct checks total). Local evidence is in `.stage2/verify.log`,
`.stage2/roundtrip.log` and the round-trip evidence JSON. These are earlier Stage 2
results, not tests created or rerun for Stage 3A.

## Stage 3A: REST security master and price storage

Implemented only in Security & Market Data on Java 21 / Spring Boot 4.1.2.
The user's override replaces the original Kafka price-notification section:
there are no market-price publishers, consumers, price outbox writes or new
messaging structures. Existing common messaging code/tables are preserved.
No new tests were created and no automated tests were run in this stage.

Added exact mappings for `security_master` and `security_price`, separate enums,
Lombok DTOs, repositories, mapper, transactional services and REST controllers.
APIs cover registration, filtered/paginated search, detail, versioned updates and
status changes, price submission, history, latest and as-of selection. ISIN
structure/check digit, currency, field limits, positive DECIMAL(20,6) and UTC
microsecond precision are validated. ISIN is immutable; securities and price
history have no delete API. Currency changes after the first price are blocked.

Price observations use the existing unique key for concurrent deduplication;
identical submissions return the stored reference and conflicting submissions
return 409. Source priority is explicit and configurable, as is freshness age.
Missing observations return 404; stale observations are returned with a flag.
Latest/as-of never select observations after their cutoff. Security lists do not
perform additional price queries. REST remains public in this service; other
services' authentication is unchanged.

## Stage 3G: MTM valuation, margin calls, and CASH top-ups

Valuation owns MTM arithmetic and authoritative Market REST price retrieval;
Custodian validates versioned inputs, applies results, records credit-exposure
deltas/history, and issues or updates one active call per contract. Loan value
uses HALF_UP(2), required CASH uses CEILING(2), shortfall is floored at zero,
and pending collateral is excluded. MTM snapshots reuse calculation_run and
calculation_result; Custodian reuses margin_call and margin_call_transaction_log.

Custodian APIs: `POST /api/contracts/{transactionReference}/margin-calls/mtm`
(required `Idempotency-Key`), `POST .../margin-calls/evaluate`, and scoped
list/detail routes. Scheduler uses each MSLA's configured MTM interval; overdue
calls move to the existing `BREACHED` status without releasing reservations.
Borrower APIs: `POST/GET /api/contracts/{transactionReference}/margin-calls/{marginCallReference}/top-ups`
and `GET` call progress. Submission writes local instruction state plus outbox;
Custodian inbox persists receipt, and only its authorized simulated CASH
confirmation updates posted collateral/call state and emits the Borrower update.

Representative flow: Borrower submits a positive CASH top-up in contract
currency with `instructionReference`; Custodian emits
`COLLATERAL_INSTRUCTION_RECEIVED` after durable persistence; operator confirms
the instruction with a unique confirmation reference. A repeated matching
confirmation returns the existing settlement; conflicting confirmation is
rejected. Margin-call updates use the existing transactional outbox/inbox path.

Changed-file groups: Valuation MTM DTO/service and existing run/result mappings;
Custodian MTM client/token, scheduler, margin-call service/controller, exposure
history mapping, and collateral-instruction handler; Borrower top-up controller,
service, and call/instruction inbox handlers; Custodian/Borrower SQL snapshots
and V3G migrations; this progress entry. Combined `00_all_databases.sql` defines
50 tables: the exposure-history table is the only new physical table, and the
Borrower contract-view version is a column migration.

Verification: latest source compiled successfully with the command above. No
tests were added or run at the user's request. Full Kafka/MySQL E2E, duplicate
delivery under runtime brokers, simultaneous scheduler instances, and cross-
custodian runtime authorization remain pending. The 3F Borrower securities-
settlement outcome projection was still a prerequisite gap when this historical
entry was written; it is now implemented, with E2E pending. Fee basis/cutoff/day-boundary rules were pending when this
historical 3G entry was written; the user later confirmed them for 3H above.

The opt-in seed contains exactly 90 real Indian equities and 10 ETFs from official
NSE CSV downloads retrieved 2026-11-07, with checksum-valid ISINs, fixed timestamps,
stable price references and clearly labelled `DEMO_SIMULATED` INR prices. Source
URLs and file hashes are recorded in the seed README. Existing rows are never
overwritten. Setup, changed-file groups, API examples, source policy and consumer
flows are documented in `STAGE3A_MARKET_DATA.md`.

Verification on 2026-11-07:

- Final `mvn -o -B -ntp "-Dmaven.repo.local=D:/yali/sl/Security-lending/.m2"
  -pl :security-market-service -am "-Dmaven.test.skip=true" clean package`
  succeeded; test compilation/execution were skipped (`.stage3a/final-build.log`).
- Manual startup against workspace MySQL 8.0.42 on port 13306 passed Hibernate
  schema validation, using a SELECT/INSERT/UPDATE-only Market account.
- Explicit seed loading produced 90 EQUITY rows, 10 ETF rows and 100 simulated
  observations. Health, unauthenticated search/latest/as-of returned successfully.
- Manual identical-price submission returned the original price reference;
  conflicting same-key submission returned 409. Invalid update returned 400.
- Restarting the final JAR with seeding enabled again kept exactly 90 equities,
  10 ETFs and 100 prices, retaining the original observation reference. Filtered
  ETF pagination returned 10 total elements. Temporary Market/MySQL processes
  started for these checks were stopped afterwards; workspace data is retained.
- No automated concurrency, locking or comprehensive regression suite was run,
  in accordance with the no-tests instruction. Build/manual observations are not
  a claim that such a suite passed.

Stage 3A stops here. MSLA setup, Borrower/Custodian selection integration,
financial calculations and external live-price feeds remain out of scope.

## Stage 3B: Custodian setup

Implemented REST endpoints for organizations, lender-custodian mandates and
MSLAs in the Custodian service, reusing the existing `organization`,
`lender_custodian_mandate` and `msla_agreement` tables. No schema migration was
needed. See [Stage 3B setup and API examples](./STAGE3B_CUSTODIAN_SETUP.md).

Operators are authorized by the existing local `CUSTODIAN_OPERATOR` role and
membership; custodian ownership is derived from that membership. Custodian
organizations are restricted to administrator-controlled setup. Lender and
borrower identities can be reused across custodians, with operator updates
blocked when shared. Mandate and agreement date ranges are validated and
overlapping duplicate relationships are rejected. MSLA eligibility and optional
default rules are passed as JSON objects and stored in the existing JSON
columns. Non-empty `default_rules` remain inactive until their business
semantics are defined; only CASH collateral with zero effective CASH haircut is
allowed. Existing REST APIs in other microservices were not changed.

The added integration tests cover MySQL JSON round trips, request validation,
mandate/MSLA date and lifecycle rules, ownership, cross-custodian isolation and
unsafe agreement changes. The disposable MySQL test URL is not configured in
this environment, so `SetupIT` execution remains pending. `mvnw clean test` and
`mvnw package` succeeded for Custodian; 11 ordinary/security tests plus the
standalone Jackson JSON-shape test passed, and all integration test sources
compiled. The packaging run completed on 2026-11-07; it did not run `*IT`
tests.

Next dependency: approve and document the typed `default_rules` business
contract before allowing non-empty rules to govern an active MSLA. Lending
workflow creation remains a later phase.

## Stage 3C: Lender lending setup

Implemented authenticated REST setup APIs for lender inventory, per-MSLA credit
limits and limit history, lending-fee rules, and collateral-haircut rules in the
Custodian service. The APIs reuse the authenticated `CUSTODIAN_OPERATOR`
membership, existing MSLA-to-mandate ownership, and current lender mandates.
Inventory changes expose only total quantity, enforce the generated
total-minus-on-loan-minus-reserved availability, and use row locks and versions.
Credit limit changes preserve the generated available amount (including
negative values), keep used/reserved values out of the setup DTOs, and audit
amount changes with user and reason. Rule writes are transactional and
serialized on the owning MSLA; active fee overlaps and haircut overlaps are
rejected. Security references and eligible asset types are checked through
Security & Market Data REST. Fee selection uses security-specific rules before
agreement defaults, as required by Prompt 08. CASH remains the only supported
collateral, with a zero haircut in the MSLA currency and coverage kept separate.

No schema migration was needed: the service-owned tables and constraints
already exist in `db/02_custodian_db.sql`. REST request samples and scope notes
are in [Stage 3C setup](./STAGE3C_LENDING_SETUP.md). No Kafka or lending
workflow/contract/settlement behavior was added.

Verification: the Custodian Maven package build passed with tests skipped, and
the focused Phase 3C test run passed all 7 tests (calculation/date/rule
selection, stale-version rejection, and no-mandate isolation). The MySQL
`SetupIT` now also covers inventory listing across custodians, but was not run
because the disposable MySQL test URL is not configured in this environment.

Known follow-up: `lender_inventory` is keyed by lender organization and ISIN,
not custodian, so two custodians with current mandates to the same lender share
the same inventory row; an approved allocation/ownership schema is needed for
per-custodian inventory. Negative available credit is preserved here, but the
later lending acceptance workflow must block new exposure when it is negative.
`default_rules` remains uninterpreted until its business contract is approved.

## Stage 3D: Borrower loan requests

Borrowers can list eligible MSLAs and atomically create/submit an idempotent
request, then list or retrieve only their own requests. Custodian validates
the existing v1 `LOAN_REQUEST_SUBMITTED` event against local borrower
membership, MSLA/mandate, security, settlement currency and optional locate
data before storing receipt. Existing outbox/inbox retry and deduplication
are reused; no loan approval, reservation, contract, settlement or FIX mapping
was added. Custodian now lists/retrieves only requests scoped by its local
operator membership → mandate → MSLA. Key changes are in Borrower
`LoanRequestService`/`LoanRequestWriter`, Custodian
`LoanRequestSubmittedHandler`/`CustodianLoanRequestQueryService`, and existing
`kafka/outbox`/`kafka/inbox` infrastructure; schema change is only nullable
`settlement_currency` on both service-owned request tables. The combined schema
remains at 48 tables. API/event examples, local Kafka setup, and changed-surface
details are in [Stage 3D loan requests](./STAGE3D_LOAN_REQUESTS.md).

Targeted package build passed; 16 request-flow tests plus 10 Custodian
authorization tests passed (zero failures, errors, or skips). Local Kafka
startup, required-topic creation and broker producer/consumer smoke check
passed; the broker was stopped afterward. Full
REST → outbox → Kafka → Custodian DB → REST E2E remains blocked because no
disposable `STAGE1_MYSQL_URL` or configured integration bootstrap was provided.
Existing `MessagingComponentIT` covers duplicate delivery, retry/rollback,
wrong destination and failed DLT acknowledgement, but is not cross-service E2E
evidence. `SENT` means broker acknowledgement only; no receipt-ack event
exists. FIX mapping is unapproved. Currency is checked against the MSLA;
matching security denomination was unresolved at 3D and is resolved in 3E:
security, MSLA, settlement, price and collateral currencies must match.
Matching Custodian-local borrower membership and JWT audiences must be provisioned.

## Stage 3E: Custodian decisions and Borrower updates

Implemented operator-only Custodian approve/reject endpoints and a Borrower
decision-event consumer. Approval rechecks party/MSLA/mandate/security/currency,
fees, CASH haircut, inventory, credit and price calculations; it reserves
inventory and credit, creates one contract and history row, changes request
status and enqueues the decision event in one Custodian DB transaction.
Rejection records its reason/status and outgoing event atomically, without a
contract or reservation. Broker publication, Borrower receipt and business
decision remain separate states. Borrower validates parties, correlation,
version, contract terms and stale/duplicate decisions, then updates its request
and approved-only contract view in the inbox transaction.

Currency rule resolved: security, MSLA, settlement, price and CASH collateral
currencies must match; no FX. Loan value uses price × whole quantity rounded
to 2 decimals HALF_UP; CASH collateral uses coverage (separate from haircut),
rounded to 2 decimals CEILING. CASH haircut is 0%. Prices must be positive,
non-future and within configurable age (24h development default). EQUITY/ETF
only; bond quote conventions remain unsupported. Reservation `expires_at` is
the start of the UTC day after requested settlement, an overdue-review deadline
only. Approval is blocked after it; active overdue reservations remain counted.
No expiry scheduler or reservation release workflow was added.

Changed surfaces: Valuation & Accrual REST calculator/API and security/price
clients; Custodian decision REST/services, locked atomic committer and outbox
route; Borrower inbox handler, owned contract-view API/table; migration
`003_phase3e_borrower_contract_view.sql`; decision event schema/sample.
Key files: `backend/custodian_service/.../LoanDecisionService.java` and
`LoanDecisionCommitter.java`; `backend/valuation_accural/.../LoanApprovalCalculationService.java`
and `calculator/LoanApprovalCalculator.java`;
`backend/borrower_service/.../LoanRequestDecisionHandler.java` and
`LoanContractView.java`; `db/00_all_databases.sql`, `db/03_borrower_db.sql`.

API: `POST /api/loan-requests/{reference}/approve`; reject with
`POST /api/loan-requests/{reference}/reject` and `{"reason":"..."}`.
Borrower reads status, reason and response time at
`GET /api/loan-requests/{reference}` and an
approved snapshot at `GET /api/loan-requests/{reference}/contract`. Event:
[approved decision example](../contracts/events/examples/loan-request-decision-approved.json).
Fresh schema now has 49 physical tables (limit 50).

Local Kafka (from repository root, retain existing `kafka-runtime/data`):
`.\kafka\bin\windows\kafka-server-start.bat .\kafka\config\kraft\server.properties`.
Create `sec-lending-responses` and `sec-lending-responses.DLT` with
`.\kafka\bin\windows\kafka-topics.bat --bootstrap-server localhost:9092 --create --if-not-exists --topic <topic> --partitions 3 --replication-factor 1`.
Existing 3D request topics are reused. Enable `KAFKA_PUBLISHER_ENABLED` and
`KAFKA_CONSUMER_ENABLED` on the respective services.

### 3D/3E verification update — 2026-11-08

| Check | Result | Evidence / boundary |
|---|---|---|
| Full reactor build and unit tests | PASS | `mvn verify`: 72 tests, 0 failures/errors/skips |
| Disposable MySQL integration | PASS | MySQL 8.0.42, separate temporary server on 13306; `mvn -Pmysql-it verify`: 91 tests, 0 failures/errors/skips |
| Kafka/MySQL round-trip | PASS | Real local Kafka + disposable MySQL; `RoundTripIT`: 1 passed. Broker outage/recovery, crash-after-ack, redelivery, duplicate handling and poison/DLT covered for generic Stage 2 events only |
| Schema ceiling | PASS | `db/validate_structure.py`: 49 tables (5/27/12/5) |
| Loan request → receipt → valuation → decision → Borrower REST | Superseded | See the final loan-specific verification below |
| Contract/reservation; rejection; concurrency; rollback; snapshot parity | Superseded | See the final loan-specific verification below |
| Overdue reservation behavior | Superseded | See the final loan-specific verification below |

Fixed test/build defects found during verification: replaced the stale
`FoundationController` test with the real public `GET /api/securities` route
while retaining actuator denial checks; added Spring REST-client starters to
Borrower, Custodian and Valuation after database-backed context startup exposed
missing `RestClient.Builder` beans. The broker round-trip fixture now uses
run-specific topics and matching inbound/DLT configuration, avoiding stale
records from prior local runs. Its Custodian test classpath is built by the
Custodian module.

This checkpoint preceded the full live simulation; its BLOCKED rows are
superseded by the final evidence below. `default_rules` semantics and
per-custodian allocation in lender-level inventory remain unresolved.
FIX mappings remain absent; JSON Kafka events are not QuickFIX.

### Final 3D/3E loan verification — 2026-11-08 (supersedes prior status)

Ran the flow against authenticated Borrower, Custodian, Market and Valuation
services, separate disposable MySQL databases and the existing local Kafka.
No cross-service database access was used. End-to-end path:
Borrower `POST /api/loan-requests` → outbox/Kafka → Custodian inbox/request →
Valuation REST → Custodian approve/reject → decision outbox/Kafka → Borrower
inbox and request/contract REST views. The API/event samples remain in
[Stage 3D loan requests](./STAGE3D_LOAN_REQUESTS.md) and the approved
[decision event](../contracts/events/examples/loan-request-decision-approved.json).

| Verification | Result | Evidence |
|---|---|---|
| `mvn verify` | PASS | 72 tests; 0 failures, errors or skips |
| `mvn -Pmysql-it verify` | PASS | MySQL integration: 91 tests; 0 failures, errors or skips |
| Real Kafka `RoundTripIT` | PASS | 1 test; generic retry, duplicate and poison/DLT coverage, not loan-specific |
| Disposable schema limit | PASS | 49 tables total: Market 5, Custodian 27, Borrower 12, Valuation 5 |
| Loan REST/Kafka flow | PASS | 13 requests: 6 approved, 1 rejected, 6 pending; 6 contracts, 6 collateral snapshots, 6 active inventory reservations; 7 decision inbox records |
| Approval/rejection and isolation | PASS | Approval snapshot matched Borrower contract view; rejection had no contract/reservation; wrong-tenant reads returned 404 |
| Idempotency and concurrency | PASS | Same-key replay had no extra effect; changed content returned 409; inventory- and credit-constrained concurrent approvals each allowed one winner |
| Rollback and duplicates | PASS | Injected Custodian and Borrower transaction failures rolled back business/outbox/inbox writes; replay committed once; duplicate request/decision events had one effect |
| Valuation and deadline rules | PASS | Missing, stale and future prices blocked approval; invalid currency/asset type rejected; overdue active reservation remained reserved |
| Broker recovery | PASS | Isolated endpoint outage preserved decision and request events; after recovery both published, and the offline request reached one Custodian inbox/request (outbox attempt 4) |

Submission sample: `POST /api/loan-requests`, `Idempotency-Key:
E2E-OFFLINE-SUBMISSION`, body uses an eligible MSLA, ISIN `INE000A01012`,
quantity `100`, settlement currency `INR`, and optional locate `null`.
Changed code/test surfaces from this verification: the Security API test now
targets `GET /api/securities`; REST-client dependencies were added to Borrower,
Custodian and Valuation; Kafka messaging test fixtures use isolated topics.
At the time of this live run, no reusable loan-E2E test file was added; see the
automated follow-up below. Loan-specific poison-message-to-DLT behavior was not
re-exercised in the live run (generic DLT coverage only). FIX mappings and
`default_rules` semantics remain unresolved; JSON Kafka events are not QuickFIX.
No settlement or later scenario was started.

Cleanup note: deleting the isolated Kafka topics failed with Windows
`AccessDeniedException` while Kafka renamed a test partition; the broker exited.
No manual edits were made to `kafka-runtime/data`; remaining topic cleanup is
unverified.

### Automated 3D/3E follow-up — 2026-11-08

- Added `LoanWorkflowE2EIT` for authenticated submission, same-key replay,
  conflicting-content rejection, Custodian receipt, approval/rejection,
  duplicate commands, contract/reservation counts, and snapshot parity.
- Extended `RoundTripIT` to send malformed JSON, unsupported loan events, and
  invalid loan-decision payloads through Borrower's real Kafka consumer; assert
  bounded retries, confirmed DLT records, no business writes, and later valid
  event handling. Added `loan-e2e` Failsafe profile; E2E requires a seeded,
  disposable fixture and four already-started services.
- Restored Borrower `spring.jpa.hibernate.ddl-auto=validate`; the full build
  exposed an existing `update` setting inconsistent with the schema-safety test.
- `mvn -pl backend/borrower_service -DskipTests test-compile`: PASS.
  `mvn verify`: PASS, 72 tests; zero failures, errors, or skips.
- Real Kafka/MySQL execution of these new checks: BLOCKED. `scripts/kafka.ps1
  -Action Status` reports broker stopped; `STAGE2_KAFKA_BOOTSTRAP` and
  `STAGE1_MYSQL_URL` are unset. Startup log records
  `AccessDeniedException` renaming
  `kafka-runtime/data/sl3d3e-requests-20261108-0`; Kafka reports all log dirs
  failed. Root cause beyond the denied move is undetermined.
- Recovery, actual loan DLT assertions, and test-topic cleanup remain BLOCKED;
  four topic partitions remain: `sl3d3e-requests-20261108-0`,
  `sl3d3e-requests-20261108.DLT-0`, `sl3d3e-responses-20261108-0`, and
  `sl3d3e-responses-20261108.DLT-0`. No data/topic directories were deleted or
  altered. Existing live-run schema evidence remains 49 tables.
  `default_rules` semantics and FIX mappings remain unresolved; no QuickFIX
  integration is claimed.
## JWT authentication update

- Removed the external OIDC issuer/JWKS dependency from Borrower, Custodian and
  Valuation. All three verify HS256 JWTs using `JWT_SHARED_SECRET`; issuer,
  audience, time claims and existing local authorization checks remain.
- Added `scripts/new-jwt-token.ps1`; setup and shared-key handling are documented
  in `STAGE1_SETUP.md`. Valuation retains its subject/client/scope restrictions.
- The same-key design means compromise of any service can allow token forgery
  for all three; keep the secret private and rotate it if exposed.
- Targeted JWT decoder tests passed for all three services (3 tests total).
