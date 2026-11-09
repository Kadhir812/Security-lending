# Stage 3B: Custodian setup

The Custodian service exposes REST setup APIs for organizations, lender mandates,
and MSLA agreements. Apply the existing `db/02_custodian_db.sql` schema; the
required columns and JSON fields already exist, so this stage adds no migration.
The service continues to use `ddl-auto=validate`.

## Custodian provisioning

Custodian organizations are provisioned only by an authorized database/setup
administrator, not by a `CUSTODIAN_OPERATOR` API call. For a new deployment,
insert the organization using the existing schema, then provision local users
and assign the existing `CUSTODIAN_OPERATOR` role using the procedure in
[Stage 1 setup](./STAGE1_SETUP.md). Do not grant roles from token claims.

```sql
-- Run as the database setup administrator; replace the example values.
INSERT INTO organization (
  organization_reference, organization_name, organization_type, status, version, created_at
) VALUES (
  'CUSTODIAN-EXAMPLE', 'Example Custodian', 'CUSTODIAN', 'ACTIVE', 0, UTC_TIMESTAMP(6)
);
```

Operators are scoped to the custodian organization stored on their authenticated
local membership. They cannot provide a custodian ID in setup requests or edit
custodian organization records. Lender and borrower references identify shared
organizations; operators can reuse an existing organization, but cannot change
details once other custodians also rely on it.

## API examples

The examples use a token whose local membership has `CUSTODIAN_OPERATOR`.
Organization references are used instead of database IDs.

Register or reuse a lender and borrower:

```http
POST /api/organizations
Authorization: Bearer <custodian-operator-token>
Content-Type: application/json

{
  "organizationReference": "LENDER-EXAMPLE",
  "organizationName": "Example Lender",
  "organizationType": "LENDER",
  "status": "ACTIVE"
}
```

```http
POST /api/organizations
Authorization: Bearer <custodian-operator-token>
Content-Type: application/json

{
  "organizationReference": "BORROWER-EXAMPLE",
  "organizationName": "Example Borrower",
  "organizationType": "BORROWER",
  "status": "ACTIVE"
}
```

Create a mandate. The custodian side is derived from the authenticated
operator; it is not part of the request.

```http
POST /api/mandates
Authorization: Bearer <custodian-operator-token>
Content-Type: application/json

{
  "mandateReference": "MANDATE-EXAMPLE-001",
  "lenderOrganizationReference": "LENDER-EXAMPLE",
  "effectiveFrom": "2026-01-01",
  "effectiveTo": "2026-12-31",
  "status": "ACTIVE"
}
```

Create an MSLA as `DRAFT`, then activate it through a versioned `PUT
/api/mslas/MSLA-EXAMPLE-001` after checking the returned `version`. The mandate
must be active and effective when the MSLA is activated. Collateral is currently
restricted to `CASH`.

```http
POST /api/mslas
Authorization: Bearer <custodian-operator-token>
Content-Type: application/json

{
  "mslaReference": "MSLA-EXAMPLE-001",
  "mandateReference": "MANDATE-EXAMPLE-001",
  "borrowerOrganizationReference": "BORROWER-EXAMPLE",
  "effectiveDate": "2026-01-01",
  "expiryDate": "2026-12-31",
  "agreementStatus": "DRAFT",
  "baseCurrency": "INR",
  "eligibleSecurities": {
    "allowed_types": ["EQUITY", "GOVT_BOND"]
  },
  "collateralType": "CASH",
  "collateralCoveragePct": 105.0000,
  "mtmIntervalMinutes": 60,
  "marginCallWindowHours": 24,
  "recallRule": "Business-day recall",
  "settlementCalendarCode": "IN-NSE"
}
```

Both `eligibleSecurities` and optional `defaultRules` are JSON objects in
requests and responses. `eligibleSecurities.allowed_types` must contain one or
more distinct supported types (`EQUITY`, `CORPORATE_BOND`, `GOVT_BOND`, `ETF`);
individual security IDs are not accepted. The business meaning and validation
contract for non-empty `defaultRules` are not defined yet. The service persists
such objects as JSON but prevents activation or lending use until that business
rule definition is approved; it does not invent financial defaults.

Get, list, and update resources through `GET /api/organizations`,
`GET /api/mandates`, and `GET /api/mslas`, with `/{reference}` for retrieval or
update. Updates require the current `version`. References and relationship
parties are immutable; MSLA terms can be changed only while `DRAFT`. The
supported MSLA transitions are `DRAFT -> ACTIVE|INACTIVE|EXPIRED`,
`ACTIVE -> INACTIVE|EXPIRED`, and `INACTIVE -> ACTIVE|EXPIRED`; `EXPIRED` is
terminal. `EXPIRED` requires an expiry date before the current date. An MSLA
already referenced by lending setup/workflow records cannot have its terms
changed, be deactivated, or be expired while those records remain.

## Verification scope

`SetupIT` exercises real MySQL JSON persistence and object-shaped API responses,
eligibility/collateral validation, active-mandate checks, status transitions,
versioning, duplicate relationship periods, shared identity protection, and
cross-custodian isolation. Run it with the project's disposable MySQL test
configuration; ordinary `mvn test` does not run `*IT` integration tests.
