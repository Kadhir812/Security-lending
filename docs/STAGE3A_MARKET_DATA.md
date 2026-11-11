# Stage 3A: security master and price storage

This stage uses authenticated HTTP REST in Security & Market Data only. All market
API routes require a trusted JWT with the configured issuer, shared HS256 secret,
audience `security-market-service`, and `MARKET_OPERATOR` or `ADMINISTRATOR` in
the `roles` claim. `/actuator/health` remains public. Price writes do not insert
outbox events or publish Kafka notifications.

## Running

Apply the existing `db/01_security_market_db.sql` to a new development database
as described in Stage 1. Do not rerun the fresh-schema SQL against populated tables.
Set `MARKET_DB_HOST`, `MARKET_DB_PORT`, `MARKET_DB_USERNAME` and
`MARKET_DB_PASSWORD` and the shared `JWT_SHARED_SECRET`. Hibernate currently
uses `ddl-auto=validate`. The application account needs SELECT, INSERT and UPDATE
on its own database only.

From the repository root (PowerShell):

```powershell
mvn -pl :security-market-service -am "-Dmaven.test.skip=true" package
$env:MARKET_SEED_ENABLED = 'true' # optional, development only; false by default
java -jar backend/security_master_market/target/security-market-service-0.0.1-SNAPSHOT.jar
```

The seed contains exactly 90 Indian equities and 10 Indian ETFs. See
[seed documentation](../backend/security_master_market/src/main/resources/seed/README.md)
for official NSE references, retrieval date, source-file hashes and selection
method. All seed prices are **simulated**, labelled `DEMO_SIMULATED`, in INR, with
fixed timestamps and stable UUID references. Restarting with the flag enabled
does not overwrite existing details or observations. An existing security whose
currency differs from INR does not receive an INR demo price.

## API

Base URL: `http://localhost:8080`. Request/response bodies are JSON. Errors use
ProblemDetail: invalid input 400, unknown security/missing applicable price 404,
duplicate ISIN/conflicting observation/outdated version 409. Unexpected errors
are sanitized. Unknown JSON fields are rejected, including an attempted ISIN
change in an update body. Enums use the exact uppercase SQL values.

| Method | Path | Behavior |
|---|---|---|
| POST | `/api/securities` | Register; 201 plus Location and security DTO |
| GET | `/api/securities` | Paginated search |
| GET | `/api/securities/{isin}` | Details |
| PUT | `/api/securities/{isin}` | Replace permitted details using current version |
| PATCH | `/api/securities/{isin}/status` | Change registration status using current version |
| POST | `/api/securities/{isin}/prices` | Insert or return identical observation; 200 |
| GET | `/api/securities/{isin}/prices/latest` | Latest applicable observation |
| GET | `/api/securities/{isin}/prices` | Paginated immutable history |
| GET | `/api/securities/{isin}/prices/as-of?at=...` | Latest applicable observation at/before cutoff |

Search supports `q` (literal substring of name, symbol or ISIN), `securityType`,
`status`, `currency`, `exchange`, `activeOnly`, `page` and `size`. Page starts at 0;
size defaults to 20 and is bounded to 1–100. Sorting is symbol then unique ISIN.
History supports inclusive `from`/`to` timestamps, `source`, `page` and `size`;
it sorts timestamp descending then local price ID descending. Responses contain
`content`, `page`, `size`, `totalElements`, and `totalPages`.

Security responses intentionally omit optional price summaries: securities with
no price remain searchable and there are no per-security price lookups on lists.

Example registration (on a database where this ISIN is not registered):

```http
POST /api/securities
Authorization: Bearer <market operator JWT>
Content-Type: application/json

{"symbol":"RELIANCE","securityName":"Reliance Industries Limited","isin":"INE002A01018","securityType":"EQUITY","currency":"INR","exchangeName":"NSE","status":"ACTIVE"}
```

The response includes the server-generated local `securityId` and initial
`version: 0`. Use ISIN, not that local ID, in other services.

```http
GET /api/securities?q=20MICRONS&activeOnly=true&size=20
GET /api/securities/INE144J01027
PATCH /api/securities/INE144J01027/status
Content-Type: application/json

{"version":0,"status":"INACTIVE"}
```

PUT uses `symbol`, `securityName`, `securityType`, `currency`, `exchangeName`,
`status` and `version`; ISIN is immutable. Currency cannot change once prices
exist, because the schema stores currency on the security rather than each
observation. Both updates and price submissions lock the local security row to
enforce this rule under concurrency. Hibernate `@Version` protects updates;
an outdated supplied version returns 409. No-op updates may retain the version.
Registration status does not represent a trading halt or lending eligibility.

```http
POST /api/securities/INE144J01027/prices
Content-Type: application/json

{"marketPrice":123.456789,"priceTimestamp":"2026-01-02T11:00:00.123456Z","sourceName":"DEMO_SIMULATED"}
```

Example response shape (UUID illustrative):

```json
{"priceReference":"9e729bd6-88f8-44d6-a74d-55534c409eff","isin":"INE144J01027","currency":"INR","marketPrice":123.456789,"priceTimestamp":"2026-01-02T11:00:00.123456Z","sourceName":"DEMO_SIMULATED","stale":true}
```

The unique database key `(security_id, price_timestamp, source_name)` arbitrates
duplicate submissions. An insert with a no-op duplicate branch followed by a
locking read returns the existing observation for an equal BigDecimal price.
A different price returns 409 without modifying history. References are UUIDs
generated server-side for new API observations; duplicates retain the original.
No delete or price-update endpoints exist. Prices must be positive, at most 14
integer and 6 fractional digits. Excess precision is rejected, not truncated.
Timestamps are UTC instants, limited to MySQL DATETIME range and microseconds.
Future observations may be stored but are excluded from latest until applicable.

## Price selection and freshness

```http
GET /api/securities/INE144J01027/prices/latest?source=DEMO_SIMULATED
GET /api/securities/INE144J01027/prices?from=2026-01-01T00:00:00Z&to=2026-01-03T00:00:00Z
GET /api/securities/INE144J01027/prices/as-of?at=2026-01-02T10:30:00Z
```

`MARKET_PRICE_SOURCE_PRIORITY` is an ordered comma-separated list, highest first;
default `DEMO_SIMULATED`. Without `source`, the first configured source with an
observation at/before the cutoff wins; choose that source's newest observation.
Unconfigured sources never win implicitly. An explicit source requests exactly
that source. Names normalize to uppercase. This policy favors source authority
over recency across sources: a stale high-priority source still wins and is
labelled stale. Configure actual source names when ingesting real data; there is
no live feed or polling scheduler in this stage.

Latest uses the current UTC time. As-of uses the supplied `at` and never selects
an observation after it. `MARKET_PRICE_MAX_AGE` defaults to `PT24H`. The returned
`stale` flag compares age to that limit (relative to `at` for as-of, current time
for latest/history). A stale observation is returned with 200; no applicable
observation returns 404. Historical reads are not rejected for age. Timestamp,
source, reference and currency are always exposed for consumer decisions.

Borrower can search active securities and select an ISIN. Custodian can retrieve
its descriptive details by ISIN. Calculation Service can request an as-of price
and retain the returned observation reference/source/timestamp as input evidence.
This stage supplies those APIs only; it does not wire those services or implement
lending workflows, MSLA eligibility or calculations.

## Changed implementation

All new Java classes live in `backend/security_master_market`: SecurityMaster and
SecurityPrice entities/repositories/services/controllers; separate SecurityType
and SecurityStatus enums; request/response DTOs; SecurityMapper; MarketInput;
MarketDataException; PricePolicy and the explicitly enabled MarketSeedLoader.
The existing ApiExceptionHandler and application.properties are extended.
Resources contain the seed JSON and provenance README. Prompt 07 records the
REST-only/no-tests override. Existing common messaging code and SQL are untouched.

See [PROGRESS.md](PROGRESS.md) for actual build and runtime verification results.
