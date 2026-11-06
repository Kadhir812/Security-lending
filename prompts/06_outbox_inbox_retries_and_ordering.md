# Prompt 06: Outbox, inbox, retries and ordering

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement local transactional outbox/inbox in each service using existing tables. A publisher claims due rows with bounded locking/leases, publishes through KafkaTemplate, waits for broker acknowledgement and updates status. Recover expired claims; retry transient failures with bounded backoff. Handle the crash after Kafka publish but before marking PUBLISHED (duplicates are expected).

Consumers use unique consumer_name+event_id; commit inbox status, business effects and response outbox atomically, then acknowledge. Do not leave RECEIVED records that permanently suppress retries. Domain rejection is a processed business response; malformed/untrusted messages are audited and dead-lettered according to policy. Ensure dead-letter publication succeeds before discarding the original record.

Increment the owning aggregate row version durably, not an outbox-derived sequence. Multiple distinct events from one state transition may share a version and must not be mistakenly dropped. Explain partition keys, version gaps, stale decisions and replay handling. Use specific retry classification and a recoverable failed-message procedure.

Test duplicate delivery, concurrent consumers, producer/consumer crashes at each commit boundary, unavailable Kafka, expired publisher lease and poison message followed by healthy messages. Do not claim Kafka exactly-once makes MySQL writes exactly-once.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
