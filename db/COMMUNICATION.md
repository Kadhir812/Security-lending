# REST and Kafka contracts

| Source | Destination | Transport | Purpose |
|---|---|---|---|
| Borrower/Custodian | Security & Market Data | REST | Security search/detail by ISIN, asset type and current status |
| Calculation | Security & Market Data | REST | Price selected by ISIN, as-of time and source; save immutable price snapshot |
| Borrower | Custodian | REST | Scoped MSLA, contracts, margins, recalls, settlement and payment detail |
| Borrower | Calculation | REST | Preview with validated input; cannot create authoritative obligations |
| Custodian | Calculation | REST | Immediate approval calculations with idempotency key |
| Custodian | Calculation | Kafka calculation-requests | Background MTM/accrual/release/payment-due runs |
| Calculation | Custodian | Kafka calculation-results | Result reference, input versions, output and failure outcomes |
| Market | Custodian | Kafka market-price-events | Schedule MTM against relevant active contracts |
| Borrower/Custodian | counterpart | Kafka sec-lending-requests / sec-lending-responses | FIX and workflow messages |
| Borrower | Custodian | Kafka sec-lending-requests / sec-lending-responses | Locate submission and decision workflow |

Additional topics are proposed for the four-service architecture. No standalone local master-data views: use REST reads; optional in-memory caches have explicit freshness limits. Custodian revalidates security status/terms at processing. Borrower request decision fields update from authorized Custodian responses. Borrower settled inventory updates from idempotent settlement events; authoritative contract balance remains Custodian-owned.

Envelope: eventId, eventType, schemaVersion, producerService, destinationService, organization scope when applicable, correlationId, causationEventId, aggregateReference, aggregateVersion, occurredAt, payload. Partition by owner aggregate. Consumer groups and ACLs enforce actual routing/security; receiver fields alone do not.

Locate request and decision payloads currently use JSON business fields in the
shared event envelope; they are not FIX-mapped messages. Locate approval
confirms current lender availability without reservation or expiry. The linked
loan approval revalidates and reserves inventory, then consumes the Locate.

A calculation request contains the complete input snapshot and a stable requestReference. Unique(requester_service, request_reference) enforces deduplication; compare request hash to reject key reuse with different inputs. Persist exact prices before compute and reuse them for retries. Corrections create new run and supersedes links. Rule versions are immutable version-controlled artifacts; persist parameters/digest in each run. Long-running work returns pending and completes through Kafka. Completed results publish atomically with outbox records.

Custodian compares all relevant versions, including collateral, contract and terms, before accepting a result. A price-driven result includes valuation time and price-source policy. Credit/inventory are revalidated under local locks. Custodian writes accepted snapshot, margin/payment obligation and outgoing event in its transaction. Borrower receives resulting business events, not permission to apply a calculation independently.

For reliable reconstruction, keep authoritative records and immutable calculations; REST endpoints expose scoped current state. Replay of messages is deduplicated. Pending messages are retained in outbox during broker outage. Jobs/messages have bounded retries, lease recovery, dead-letter handling and operational replay. Missing master data stops the affected action rather than bypassing validation.
