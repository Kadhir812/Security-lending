# Prompt 03: Map persistence exactly to MySQL

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement service-local JPA entities/repositories for the owned tables needed by upcoming stages, grounded in SQL. Use Java enums matching SQL values, BigDecimal for DECIMAL, Instant/UTC mapping for DATETIME and LocalDate for DATE. Map JSON explicitly with Hibernate JSON typing and schema-versioned DTOs. Map generated available/outstanding/shortfall columns read-only (insertable=false, updatable=false).

Use version columns for optimistic concurrency; use row locks where reservations, settlement effects or payment totals require serialization. Define transaction boundaries and indexes for due work. External ISIN/reference columns stay scalars; no remote @ManyToOne. Use provided uniqueness constraints for business references and idempotency.

Do not use JPA cascade delete for financial/audit records. Do not put @Version onto immutable imported history without defining its meaning. Model status changes and outbox records in the same local transaction.

Test round-trip decimal precision, JSON, generated columns, duplicate reference rejection, FK enforcement and concurrent updates using real MySQL (Testcontainers when Docker is available, or separately provisioned MySQL). Never silently skip an unavailable integration test and label it passed.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
