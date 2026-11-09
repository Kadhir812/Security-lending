# Prompt 07: Security master and market prices

## Stage 3A override (user instruction, 2026-11-07)

Use HTTP REST only for Security & Market Data in this stage. Do not publish
market-price Kafka events or implement price consumers. Preserve existing common
messaging tables and code without expanding them. REST authentication is not
required for this service; do not alter other services' authentication.
Store security details and immutable price history; expose search, details,
latest, historical and as-of REST APIs. Do not create tests for this stage.
This override takes precedence over conflicting Kafka, authentication and test
instructions below and in the original Stage 3A attachment.

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement Security & Market Data APIs to register/update instruments and ingest prices, plus paginated search and detail by ISIN. Validate ISIN format, asset-type ENUM, currency and status. Search by name/symbol; never treat symbol alone as globally unique. Preserve inactive instruments/history.

Prices must be positive, timestamped, source-labelled, immutable and deduplicated by the existing unique key; assign stable price_reference. Define as-of selection, permitted price age/source and behavior for missing/future/stale data. Never choose a future price for historical valuation. Repeated identical ingestion returns the existing observation; conflicting same-key observations produce an explicit conflict/correction process.

Publish price notifications atomically using outbox. Calculation fetches exact price observations through authenticated REST and saves its own immutable input evidence. Custodian selects relevant contracts when a price notification arrives; no direct Market DB query.

Trading halt/resumption and instrument registration ACTIVE/INACTIVE are separate concepts. Implement the minimal agreed field patch from prompt 01 if needed and preserve source/timestamp provenance. Test price selection, stale data, duplicate ingestion, unauthorized writes and query filtering.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
