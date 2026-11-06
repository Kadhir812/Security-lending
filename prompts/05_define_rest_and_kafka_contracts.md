# Prompt 05: Define REST and Kafka contracts

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Create OpenAPI contracts under contracts/rest and versioned event schemas/examples under contracts/events. Include correlation and idempotency behavior, error status, pagination, money units, timestamps, null semantics and authorization. Implement local DTO validation and contract tests.

REST covers securities/prices, scoped MSLA/contracts/obligations, synchronous previews and approval calculations. Kafka original topics are sec-lending-requests and sec-lending-responses; add calculation-requests, calculation-results and market-price-events with corresponding dead-letter policy. Define ownership-aware routing because AF and workflow messages travel in both directions. A receiver field alone is not broker routing; each destination has its own consumer group and handler allowlist.

Envelope fields: eventId, eventType, schemaVersion, producerService, destinationService, sender/receiver organization references when applicable, correlationId, causationEventId, aggregateReference, aggregateVersion, occurredAt and payload. Embed raw FIX with actual SOH characters (JSON escaped on transport), profile/version metadata, and explicitly defined non-FIX companion data.

Define events for locate response, loan acceptance/rejection, collateral posting, delivery/return confirmation, margin call, recall, fees/rebates, payments and closure. Consumers tolerate additive fields but reject unsupported major schema versions. Include examples for success, rejection and duplicate delivery.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
