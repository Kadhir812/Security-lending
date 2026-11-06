# Prompt 07: Security master and market prices

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement Security & Market Data APIs to register/update instruments and ingest prices, plus paginated search and detail by ISIN. Validate ISIN format, asset-type ENUM, currency and status. Search by name/symbol; never treat symbol alone as globally unique. Preserve inactive instruments/history.

Prices must be positive, timestamped, source-labelled, immutable and deduplicated by the existing unique key; assign stable price_reference. Define as-of selection, permitted price age/source and behavior for missing/future/stale data. Never choose a future price for historical valuation. Repeated identical ingestion returns the existing observation; conflicting same-key observations produce an explicit conflict/correction process.

Publish price notifications atomically using outbox. Calculation fetches exact price observations through authenticated REST and saves its own immutable input evidence. Custodian selects relevant contracts when a price notification arrives; no direct Market DB query.

Trading halt/resumption and instrument registration ACTIVE/INACTIVE are separate concepts. Implement the minimal agreed field patch from prompt 01 if needed and preserve source/timestamp provenance. Test price selection, stale data, duplicate ingestion, unauthorized writes and query filtering.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
