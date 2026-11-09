# Stage 3C: Lender lending setup

Stage 3C adds REST management for lender inventory, MSLA credit limits, lending
fees, and collateral haircuts in the Custodian service. It reuses the Phase 3B
authentication, organization, mandate, and MSLA boundaries. The required tables
already exist in `db/02_custodian_db.sql`; no migration is needed. Hibernate
continues to validate the deployed schema.

All routes require an authenticated local membership with the
`CUSTODIAN_OPERATOR` role. The custodian is derived from that membership, never
from request data. MSLA resources are scoped through
`msla_agreement.mandate_id -> lender_custodian_mandate.custodian_org_id`.
Inventory is scoped through the lender organization and a current active
lender-custodian mandate. Security reference data is fetched from the
Security & Market Data REST API using
`SECURITY_MARKET_BASE_URL` (default `http://localhost:8080`); no other service
database is queried.

## Inventory

`totalQuantity` is the only mutable quantity. `onLoanQuantity` and
`reservedQuantity` are controlled by future lending workflows and are omitted
from write DTOs. `availableQuantity` remains the schema calculation
`total - on-loan - reserved`. Updates require the current `version`, lock the
lender/inventory rows transactionally, and cannot reduce total below encumbered
quantity. Duplicate lender/security rows are rejected.

The existing table identifies an inventory row by `organization_id` (the
lender) and `security_isin`, not by custodian. Thus if the same lender has
current mandates with multiple custodians, each authorized custodian can see
the same lender-owned inventory row; the schema cannot represent separate
custodian allocations. This implementation does not invent a custodian mapping.
Add an approved schema ownership/allocation design before requiring independent
per-custodian inventory.

```http
POST /api/inventory
Authorization: ******
Content-Type: application/json

{
  "lenderOrganizationReference": "LENDER-EXAMPLE",
  "securityIsin": "INE002A01018",
  "totalQuantity": 10000
}
```

```http
GET /api/inventory?page=0&size=20
GET /api/inventory/LENDER-EXAMPLE/INE002A01018
PUT /api/inventory/LENDER-EXAMPLE/INE002A01018
Authorization: ******
Content-Type: application/json

{
  "totalQuantity": 12000,
  "version": 0
}
```

## Credit limits

One credit-limit row is managed per MSLA. Its custodian ownership is checked
through the MSLA's mandate. The database-generated available amount remains
`limit - used - reserved`, and may be negative after limit or valuation changes.
This setup API never accepts used/reserved values; future lending acceptance
must block new exposure when available credit is negative. Limit create/update
requires an audit reason, and each actual limit change records the authenticated
user and time in `credit_limit_history`.

```http
POST /api/credit-limits/MSLA-EXAMPLE-001
Authorization: ******
Content-Type: application/json

{ "limitAmount": 5000000.00, "reason": "Initial approved MSLA limit" }
```

```http
GET /api/credit-limits
GET /api/credit-limits/MSLA-EXAMPLE-001
GET /api/credit-limits/MSLA-EXAMPLE-001/history
PUT /api/credit-limits/MSLA-EXAMPLE-001
Authorization: ******
Content-Type: application/json

{
  "limitAmount": 4500000.00,
  "status": "ACTIVE",
  "version": 0,
  "reason": "Quarterly limit review"
}
```

Reductions below current used plus reserved exposure are allowed and can produce
negative available credit; no workflow exposure is modified by this phase.

## Lending fee rules

Rules may be agreement-default (`securityIsin: null`) or specific to an active
security. Security-specific rules must reference an active Security & Market
Data record whose type appears in the MSLA's existing
`eligible_securities.allowed_types`. Rates are nonnegative and fit the existing
`DECIMAL(10,6)` column. Active rules with the same scope cannot overlap; fee
dates are inclusive, matching the schema's `effective_to >= effective_from`.

The Phase 3C setup requirement's established selection is security-specific
first, then agreement-default fallback, for the business date. Inactive rules
are ignored. The effective-rule endpoint exposes that selection but does not
create a loan or snapshot contract data.

```http
POST /api/mslas/MSLA-EXAMPLE-001/lending-fees
Authorization: ******
Content-Type: application/json

{
  "securityIsin": null,
  "annualFeePct": 0.500000,
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null,
  "status": "ACTIVE"
}
```

```http
GET /api/mslas/MSLA-EXAMPLE-001/lending-fees
GET /api/mslas/MSLA-EXAMPLE-001/lending-fees/effective?securityIsin=INE002A01018&businessDate=2026-03-01
```

Updates to fee rules require their current `version`. The service serializes
rule writes by locking the owning MSLA, preventing concurrent overlapping
active rules.

## Collateral haircuts

The supported collateral workflow remains CASH-only. Rules require type `CASH`,
currency equal to the MSLA base currency, and exactly `0.0000` valuation
haircut. `collateralCoveragePct` remains a separate MSLA term and is never
treated as a haircut. Effective intervals use `[effectiveFrom, effectiveTo)`;
touching intervals do not overlap. The existing haircut table has no status
column, so all stored rules participate in overlap validation.

```http
POST /api/mslas/MSLA-EXAMPLE-001/collateral-haircuts
Authorization: ******
Content-Type: application/json

{
  "collateralType": "CASH",
  "currency": "INR",
  "haircutPercentage": 0.0000,
  "effectiveFrom": "2026-01-01T00:00:00Z",
  "effectiveTo": null
}
```

Get/list/update routes are available under the same MSLA path; updates require
the row's current `version`.

## Scope and known boundaries

This phase does not implement borrower requests, lending approvals,
reservations, contracts, settlement, or `loan_contract.collateral_terms`.
`default_rules` remains unchanged and is not interpreted; its business
semantics still require approval. No Kafka publishing or consumption was added.

The separate Custodian schema stores inventory and security-specific fee
references as ISINs without cross-service foreign keys. A combined reference
schema file may represent those references as `security_id`; the Custodian
service uses the service-owned `db/02_custodian_db.sql` schema and validates
those ISINs through REST, not cross-database joins.
