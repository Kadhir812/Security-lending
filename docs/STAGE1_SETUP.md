# Stage 1: persistence and identity foundation

Java 21 and Spring Boot 4.1.2 are pinned in the root Maven POM. Each child builds
an independent executable JAR; no child depends on another service's code.
The existing directory names are retained, including `valuation_accural`.

## Build and run

From the project root:

```powershell
mvn verify
mvn -pl backend/borrower_service package
# Equivalently select the Maven artifact:
mvn -pl :borrower-service package
```

For independent CI, publish/install the parent POM with `mvn -N install` (or
`mvn -N deploy` with your private repository configuration). Build a child with
its own POM. There is no shared entity or application JAR.

Apply `db/01_security_market_db.sql` through `db/04_valuation_accrual_db.sql` to
empty databases, using a migration administrator. Do not also apply the combined
script. Adapt `db/05_permissions_template.sql` with separate secrets supplied
outside Git. Runtime accounts need SELECT, INSERT and UPDATE only on their own
database. Hibernate uses `ddl-auto=validate`; automatic SQL initialization and
open-session-in-view are disabled.

| Service | Port | Database | Environment prefix | JWT audience |
|---|---:|---|---|---|
| Security & Market Data | 8080 | security_market_db | MARKET | none |
| Custodian | 8081 | custodian_db | CUSTODIAN | custodian-service |
| Borrower | 8082 | borrower_db | BORROWER | borrower-service |
| Valuation & Accrual | 8083 | valuation_accrual_db | VALUATION | valuation-service |

For each service, set `<PREFIX>_DB_PASSWORD`. Optional overrides are
`<PREFIX>_DB_HOST`, `<PREFIX>_DB_PORT`, `<PREFIX>_DB_USERNAME` and `<PREFIX>_PORT`.
The database name itself is fixed to the service's owned database. Default users
are `<database>_app`. Do not use root/application-wide credentials.

Borrower, Custodian and Valuation verify HS256 JWTs signed with the same
`JWT_SHARED_SECRET`. `JWT_ISSUER` defaults to `securities-lending`; optional
`<PREFIX>_JWT_AUDIENCE` overrides must match the token audience. Valuation additionally requires
`VALUATION_PREVIEW_SUBJECT` and `VALUATION_CUSTODIAN_SUBJECT`; these must be
distinct, exact service-account subjects. Client IDs default to `borrower-service`
and `custodian-service`, with overrides `VALUATION_PREVIEW_CLIENT_ID` and
`VALUATION_CUSTODIAN_CLIENT_ID`.

### Shared JWT setup

The same shared signing key is used by Borrower, Custodian and Valuation. All
local user, role, organization and token claim checks remain enabled.

Generate one random secret once and set the same Base64 value in all three
service terminals:

```powershell
$bytes = [byte[]]::new(32)
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SHARED_SECRET = [Convert]::ToBase64String($bytes)
$env:JWT_SHARED_SECRET
```

In another terminal, set that same secret and generate a token. Each `sub` must
already exist as an active user in the service database and the organization
claim must match. Roles continue to come only from local database assignments:

```powershell
$env:JWT_SHARED_SECRET = '<same-base64-secret>'
.\scripts\new-jwt-token.ps1 -Audience borrower-service -Subject '<borrower-sub>' `
  -OrganizationReference '<borrower-org>' | Set-Clipboard
```

For Custodian use audience `custodian-service` and its active organization and
operator subject. For Valuation use audience `valuation-service`, plus
`-ClientId borrower-service -IdentityKind service -Scope valuation.preview`
for preview, or `-ClientId custodian-service -IdentityKind service -Scope
valuation.authoritative` with the configured Valuation service subject. Tokens
expire after 15 minutes by default (maximum 60). Keep the secret outside the
repository and do not paste it into logs or documentation.

Start a packaged service, for example:

```powershell
java -jar backend/borrower_service/target/borrower-service-0.0.1-SNAPSHOT.jar
```

`GET /api/foundation/status` returns only the service name and implemented stage.
It requires local roles in Borrower/Custodian and service scopes in Valuation.
Security & Market Data's application endpoint is public and needs no issuer,
decoder or login. `/actuator/health` is public without details; other actuator
paths are denied and not exposed. There are no production test/admin APIs.

## JWT identity and authorization

The services verify HS256 signatures locally; they do not contact an identity
provider or expose a token-issuance API. Issuer, expiry, not-before, audience,
subject and organization are validated. Generate signed tokens with the helper
above or an equivalent trusted tool. Do not accept unsigned tokens or tokens
from untrusted clients.

1. Use `scripts/new-jwt-token.ps1` to sign a short-lived token with the shared
   secret and the correct service audience.
2. Set `sub` and `organization_reference` to an active, provisioned local user
   and that user's organization.
3. Keep `JWT_SHARED_SECRET` out of source control and logs.
4. Assign `TRADER`, `CUSTODIAN_OPERATOR` or `ADMINISTRATOR` through local
   `roles` and `user_roles`. Token role claims never grant local roles.
5. Valuation checks configured client ID, subject, identity kind and scope. Only
   the configured Custodian identity receives authoritative calculation access.

Example decoded Borrower claims (illustrative, not a usable signed token):

```json
{
  "iss": "securities-lending",
  "sub": "provider-assigned-user-subject",
  "aud": ["borrower-service"],
  "exp": 1893456000,
  "organization_reference": "BORROWER-A",
  "roles": ["TRADER"]
}
```

Example decoded Custodian service token for Valuation:

```json
{
  "iss": "securities-lending",
  "sub": "provider-assigned-custodian-service-subject",
  "aud": ["valuation-service"],
  "exp": 1893456000,
  "client_id": "custodian-service",
  "identity_kind": "service",
  "scope": "valuation.authoritative",
  "organization_reference": "BORROWER-A"
}
```

Example request after generating a signed token:

```powershell
Invoke-RestMethod http://localhost:8082/api/foundation/status -Headers @{Authorization="Bearer $env:ACCESS_TOKEN"}
Invoke-RestMethod http://localhost:8080/api/foundation/status
```

The local Borrower user must be active and its `organization_reference` must
exactly match the verified token. Custodian additionally requires an active local
organization of type CUSTODIAN. Comparisons remain case-sensitive even though the
supplied SQL collation is case-insensitive. There is no automatic user registration.

Future resource handlers must use `OrganizationAuthorization` with the organization
read from the resource, not merely a body field. `MandateAuthorization` requires
the operator role, an active mandate within its effective dates, and the current
Custodian organization. It queries only local tables. The existing schema supports
organization-level mandate access, not individual user-to-mandate assignments;
Stage 1 adds no assignment tables or business APIs. `ADMINISTRATOR` does not bypass
organization checks or automatically become a mandate operator.

`CalculationAuthorization` distinguishes preview from authoritative scope and
matches the resource organization to the verified token. The converter removes
authoritative permission from any identity other than the configured Custodian
client/subject pair. No user/role repositories exist in Valuation.

Reference: [Spring Security JWT resource-server validation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## Verification

`mvn verify` runs ordinary tests, including HTTP requests with real RSA-signed
test tokens through the security filter chain. It does not claim to test MySQL.
Keys are generated in memory for each test JVM; no reusable signing key is stored.

The `mysql-it` profile runs application startup/schema validation and persistence
tests against an **explicitly supplied disposable MySQL 8 server**:

```powershell
$env:STAGE1_MYSQL_URL = 'jdbc:mysql://127.0.0.1:13306/'
$env:STAGE1_MYSQL_ADMIN = 'root'
# Set STAGE1_MYSQL_ADMIN_PASSWORD if the disposable server requires one.
mvn -Pmysql-it verify
```

The administrator is used only by the fixture to apply the original individual
SQL scripts to empty schemas and create per-service accounts with random ephemeral
passwords. Spring uses those restricted accounts. Tests roll back their rows.
Do not point this profile at production or shared databases: the fixtures use the
real four database names and manage test-only accounts named `stage1_<database>`.
An unavailable/misconfigured MySQL server fails the profile; it is never silently
skipped. Reports are under each module's `target/surefire-reports` and
`target/failsafe-reports`.

The authorization controllers used for cross-organization/mandate/calculation
checks exist only in test sources, excluded from production component scanning
and packaged JARs. Tests exercise persistence only: no Kafka publishing, consuming,
retry loops, FIX adapters, calculators, settlements or lending scenarios exist.

## Coding conventions and reuse

Borrower was the source pattern for all three messaging entities/repositories,
JWT validation, HTTP security, correlation IDs, exception translation and tests.
Copies are service-local and adapted for package, database, audience and policy.
Custodian reuses user/role mappings with its organization FK. Valuation reuses JWT
validation with service scopes instead of local users; Market uses explicit public
application security and no JWT configuration.

Entities use Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, private all-arguments
constructors and `@Builder.Default`. Explicit equality compares non-null persistent
IDs; transient objects remain distinct and hash codes stay stable. `toString`
includes only the ID, so lazy relationships and payloads are not traversed/logged.
Enums are separate files under `enums/`. Constructor-injected components use
`@RequiredArgsConstructor` where no custom validation or superclass call is needed.
