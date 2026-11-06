# Prompt 01: Audit schema and define states

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Inspect all four SQL scripts and map every feature to existing tables/columns. Run the SQL against a fresh real MySQL test instance if available; otherwise report that block and do static checks. Confirm exactly 48 application tables, local FKs, credential isolation and no automatic DDL. Build entity-to-table and external-reference maps.

Define legal state transitions for locate, loan request, contract, collateral, settlement, margin, recall, order and payment. Keep independent dimensions explicit: recalled loan may still have outstanding quantity; partial return need not imply CLOSED. List business invariants not enforced by CHECK constraints. Identify field gaps (for example precise HALTED/RESUMED instrument status versus ACTIVE/INACTIVE registration status) and propose minimal column patches without adding tables. Do not conflate those statuses.

Confirm organization registry ownership, issuer/subject identity, version semantics, applied calculation evidence and idempotent payment obligations. Document endpoint authorization roles and required bootstrapping order. Verify all requested financial record types fit calculation_result rather than adding one table per calculation.

Deliver docs/SCHEMA_REVIEW.md and docs/STATE_TRANSITIONS.md; report test results. Do not implement business logic until names and invariants are reconciled.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
