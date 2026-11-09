# Stage 2: common Kafka messaging

This stage adds messaging infrastructure only. It reuses Stage 1's local JPA
entities, MySQL connections and REST security. It does not implement lending,
security-master, MSLA, financial calculation, FIX, settlement or payment workflows.
The schema remains the supplied 48 tables.

## Flow and transaction boundaries

1. A caller opens a **local writable database transaction** and calls
   `OutgoingEventService.enqueue(aggregateType, envelope)`. This method requires
   that transaction; it never silently creates a separate one. The outbox and
   OUTBOUND/PENDING audit row commit or roll back with the caller's effects.
2. `OutboxPoller` calls `OutboxPublisher`. `OutboxStore` claims one due row in a
   short transaction with `FOR UPDATE SKIP LOCKED`. The claim gets a unique owner
   token, a database-time lease and an incremented attempt count. Network calls
   happen after releasing database locks.
3. The producer uses `acks=all`, idempotent Kafka production and bounded delivery
   waits. Only a successful broker acknowledgment permits a PUBLISHED update.
   A conditional update checks the current owner token and unexpired lease, so
   an old worker cannot overwrite a newer owner's result.
4. `KafkaMessageListener` delegates to `MessageReceiver` and a proxied
   `InboxProcessor`. The inbox transaction obtains the unique
   `(consumer_name,event_id)` lock, invokes the registered handler, saves any
   correlated response through the same outbox service, marks the inbox
   PROCESSED and records the inbound audit. Acknowledgment occurs after commit.
5. A duplicate PROCESSED event returns without invoking its handler. A RECEIVED
   event is unfinished work and is processed again. A reused event ID with
   different canonical content is quarantined, not accepted as a duplicate.

The existing JSON `payload` columns in messaging tables contain the **complete
canonical envelope**, including its nested business `payload`. This preserves
`occurredAt` and `causationEventId` without adding columns. Existing metadata
columns remain populated for queries. JSON is normalized before persistence so
MySQL/Java integer representations do not cause false duplicate conflicts.

There is no atomic transaction spanning Kafka and MySQL. A crash after Kafka
publication and before marking PUBLISHED causes redelivery after lease expiry.
A crash after inbox commit and before offset acknowledgment also causes
redelivery. Transactional inbox deduplication prevents repeated local effects
when handlers obey the transaction boundary. This is **at-least-once delivery**,
not a claim of Kafka/MySQL exactly-once processing. Handlers must not perform
untracked external side effects inside the inbox transaction.

## Envelope, routing and ownership

See [envelope-v1.schema.json](../contracts/events/envelope-v1.schema.json) and the
[test request](../contracts/events/examples/stage2-request.json)/
[test response](../contracts/events/examples/stage2-response.json) examples.
Required fields are validated, UUID event IDs use canonical lowercase text,
schemaVersion must be 1, aggregateVersion must be nonnegative, and occurredAt is
an ISO instant. Optional causation/organization fields may be absent or null.
Additional envelope fields are tolerated but not interpreted or persisted as
part of the canonical envelope. Runtime validation also bounds UTF-8 envelope
size and rejects token/credential keys recursively in payloads.

The record key must equal `aggregateReference`, a stable external reference,
not a remote database ID. Callers must namespace references appropriately.
Every route explicitly matches **producer + destination + event type + topic**.
The destination must be the receiving service, and a handler must be registered
for the permitted event type. Unknown destinations/types/versions go through
the rejection policy. There is no permissive fallback handler.

| Service | Default consumer group/logical consumer | Inbound topic | Stage 2 production handlers |
|---|---|---|---|
| Borrower | borrower-messaging-v1 | sec-lending-responses | none |
| Custodian | custodian-messaging-v1 | sec-lending-requests | none |
| Valuation | valuation-messaging-v1 | none | none |
| Security & Market Data | market-messaging-v1 | none | none |

Infrastructure is implemented first in Borrower, then copied with local packages
to all services. The common code is the same; service identity, database,
consumer/group names and inbound topics differ. Market and Valuation have no
lending-topic listeners. Consumer and publisher activation default to false;
production route allowlists are empty until a later stage adds approved handlers.
Enabling a consumer without a matching inbound route fails configuration validation.

Only test classes register `STAGE2_PING` and `STAGE2_PONG`. Test handlers are not
included in executable application JARs and have no HTTP endpoint.

Replica instances must use the same stable logical consumer name and group for
the same responsibility. Changing the logical consumer name creates a new
deduplication namespace and must be treated as an explicit reprocessing decision.

## Ordering and aggregate versions

Outbox claims block a later row for the same service/partition key until earlier
rows are PUBLISHED. FAILED rows also block that key until operational repair;
other keys continue. Kafka ordering applies within a topic partition, not
globally across request/response topics or services. Crash-window duplicate
deliveries can still occur.

Aggregate versions come from the caller's durable owner row in its local
transaction. They are never calculated from outbox counts or assigned by this
infrastructure. The test flow uses a fixed fixture version and creates no
business table. Multiple distinct event IDs at one version are processed, not
discarded. Future business handlers must define what a version gap or stale
decision means for their own aggregate (reload/defer/reject/reconcile); Stage 2
does not invent a universal business ordering rule.

## Retry, dead-letter and repair policy

Producer timeouts/retriable Kafka errors return a claimed row to PENDING with
exponential bounded backoff. Permanent producer errors and exhausted attempts
become FAILED, preserving the row and failure classification. An expired
PUBLISHING lease can be claimed again; exhausting the budget through repeated
crashes also becomes FAILED. Broker downtime therefore preserves outgoing work:
initially PENDING, then FAILED if the configured retry budget is exhausted.

Consumer retries are limited to classified transient database failures,
`RetryableMessageException` and Kafka delivery failures. Unexpected/permanent
failures are quarantined. Envelope/routing errors take the quarantine path
immediately. The error handler never treats a failed dead-letter send as recovery.

Quarantine first commits a REJECTED audit row, then synchronously waits for the
dead-letter broker acknowledgment. Only then may the original offset be committed.
If the database or dead-letter broker is unavailable, the original remains
unacknowledged. A retry uses the same deterministic rejection ID, derived from
logical consumer + original topic/partition/offset. Duplicate DLT delivery remains
possible and operators can deduplicate it by that rejection ID.

Untrusted raw bodies and headers are **not** copied into the audit or DLT; they
could contain credentials. Rejection records contain reason, source location,
logical consumer and SHA-256 digest. Default DLT names are
`sec-lending-requests.DLT` and `sec-lending-responses.DLT`. They are diagnostic
records, not business envelopes, and application listeners do not subscribe to them.

Operational recovery (no public administrative API is added):

1. Investigate the sanitized failure code and correlation/event ID. Repair broker
   access, dependency availability, deployment routing or the handler first.
2. With controlled operational credentials on the **owning database**, reset a
   reviewed FAILED outbox row to PENDING, clear lock/error fields, reset its attempt
   count and set `next_attempt_at=UTC_TIMESTAMP(6)`. Preserve event ID and payload.
   Confirm the original owner transaction committed before replaying.
3. For DLT recovery, retrieve the original source record from Kafka by its stored
   location, verify its digest, and inspect it in a restricted environment. Retain
   source topics long enough for the operational repair SLA; after source retention
   expires, the redacted DLT cannot reconstruct the original body.
4. Replay an unchanged valid event with the same key/event ID and logical consumer.
   PROCESSED rows remain deduplicated; rolled-back/RECEIVED work can resume. A
   corrected logical event needs a new ID linked by causation. Do not delete inbox
   records or change consumer names to bypass deduplication.

## External configuration and broker access

Common settings are bound under `app.messaging`:

| Setting | Default |
|---|---|
| bootstrap-servers | localhost:9092 (`KAFKA_BOOTSTRAP_SERVERS`) |
| consumer-enabled / publisher-enabled | false (`KAFKA_CONSUMER_ENABLED` / `KAFKA_PUBLISHER_ENABLED`) |
| group-id | service-specific (`KAFKA_GROUP_ID`) |
| inbound-topic / dead-letter-topic | table above (`KAFKA_INBOUND_TOPIC` / `KAFKA_DEAD_LETTER_TOPIC`) |
| poll-interval-ms / batch-size | 1000 / 20 |
| send-timeout-ms / lease-ms | 5000 / 30000 |
| max-attempts | 8 |
| initial-backoff-ms / max-backoff-ms | 1000 / 60000 |
| consumer-retries | 3 |
| max-envelope-bytes | 262144 |

All settings can be supplied through Spring configuration/environment. The lease
must exceed three send timeouts. Routes are a list of `EventRoute` entries, with
`event-type`, `producer-service`, `destination-service`, and `topic`. Defaults do
not grant permission for test or business types. Consumer auto-commit is disabled
and MANUAL_IMMEDIATE acknowledgment is used; there is no automatic topic creation.
Provision request, response and DLT topics with appropriate replication/retention.

Supply Kafka security settings separately for **each service**, for example in
an external properties file whose secrets are provided by the environment:

```properties
app.messaging.client-properties[security.protocol]=SASL_SSL
app.messaging.client-properties[sasl.mechanism]=SCRAM-SHA-512
app.messaging.client-properties[sasl.jaas.config]=${KAFKA_SASL_JAAS_CONFIG}
app.messaging.client-properties[ssl.truststore.location]=${KAFKA_TRUSTSTORE_PATH}
app.messaging.client-properties[ssl.truststore.password]=${KAFKA_TRUSTSTORE_PASSWORD}
```

Use distinct authenticated broker principals and narrow ACLs: Borrower writes
requests and reads responses with its own group; Custodian reads requests with
its own group and writes responses; each can write its relevant rejection DLT.
Operations principals manage DLT inspection/replay. Envelope labels are not
cryptographic producer identity, so enforce producer topic ownership through
broker ACLs. Security Master's public REST exception **does not** remove Kafka
TLS/SASL/ACL requirements. No real broker credentials are committed.

## Start and verify

For local Kafka, the existing workspace scripts are sufficient:

```powershell
.\scripts\setup-kafka.ps1
.\scripts\kafka.ps1 -Action Start
```

Use disposable MySQL and Kafka instances for the integration harness. The MySQL
URL must end with `/` and omit a database name. Fixture provisioning uses
`STAGE1_MYSQL_ADMIN` / `STAGE1_MYSQL_ADMIN_PASSWORD`; application contexts receive
separate generated per-database credentials. Do not use a production/shared DB.
The harness uses the real request/response topic names, so do not point it at a
broker carrying business traffic.

```powershell
# Existing disposable local infrastructure; adjust ports for your environment.
.\scripts\verify-messaging.ps1 `
  -MySqlUrl 'jdbc:mysql://127.0.0.1:13306/' `
  -KafkaBootstrap '127.0.0.1:19092'
```

The verification script first runs all four modules' persistence/security and
messaging component tests, preparing separate test runtime classpaths. It then
starts Borrower's application context and launches Custodian in a **separate JVM**
with test-only handlers. Custodian starts after Borrower publishes, proving
consumer downtime/recovery. The harness stops Custodian when finished and writes
`.stage2/roundtrip-<run-id>/evidence.json` plus its startup log. No service-to-service
Maven dependency or shared JPA artifact is used.

Equivalent commands (from the project root):

```powershell
$env:STAGE1_MYSQL_URL='jdbc:mysql://127.0.0.1:13306/'
$env:STAGE2_KAFKA_BOOTSTRAP='127.0.0.1:19092'
mvn -Pmessaging-it clean verify
mvn -Pround-trip-it -pl :borrower-service clean verify
```

To start the packaged services normally, set the Stage 1 DB/OIDC variables and
run their JARs in separate terminals. Common messaging stays idle without explicit
activation/approved handlers. Test-only ping/pong handlers are deliberately absent:

```powershell
java -jar backend/borrower_service/target/borrower-service-0.0.1-SNAPSHOT.jar
java -jar backend/custodian_service/target/custodian-service-0.0.1-SNAPSHOT.jar
```

For each successful unique request, expect:

| Database | Rows |
|---|---|
| borrower_db | one PUBLISHED request outbox; one OUTBOUND/PUBLISHED request audit; one PROCESSED response inbox; one INBOUND/PROCESSED response audit |
| custodian_db | one PROCESSED request inbox; one INBOUND/PROCESSED request audit; one PUBLISHED response outbox; one OUTBOUND/PUBLISHED response audit |

Request and response share `correlationId`; response `causationEventId` points to
the request. Duplicate deliveries with the **same logical consumer name** do not
create another response/effect. The poison test adds a REJECTED Custodian audit
and a diagnostic request-DLT record. The harness sends a second healthy request
after poison, so a successful full run has two request/response pairs.

Component tests run against real MySQL in every service; Kafka sends are mocked
there to exercise deterministic fault boundaries. The round-trip test uses the
real Kafka client and broker. Broker downtime is exercised with an unavailable
producer endpoint, then recovery to the live broker. Crash windows are injected
at exact method boundaries; they do not claim to kill the operating system/JVM.
Missing infrastructure fails the selected integration profile; no tests silently
skip. Actual results and remaining deployment setup are recorded in PROGRESS.md.

Reference: [Spring Kafka error recovery and commit behavior](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html).
