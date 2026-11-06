# Prompt 25: Observability and operational reliability

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement structured logs with event/correlation/business references and safe redaction, Actuator health and Prometheus metrics. Track REST errors/latency, Kafka lag, outbox age/retries, dead-letter counts, pending calculation age, stale results, settlement failures, open margins, overdue recalls and scheduled-job lateness.

Apply outbound REST timeouts and bounded retries only where idempotent. Avoid a shared threadpool starvation caused by long calculations; use bounded worker capacity and admission/backpressure. Recover job/outbox leases after restart. Ensure one instance's scheduler does not double-run logical work when another instance is active. Use existing row locks/idempotency, not a new scheduler-lock table by default.

Document graceful shutdown, separate per-service environment/DB credentials, native Java process launch and optional container testing. Kafka broker infrastructure and OIDC provider are not extra lending microservices. Do not require Kubernetes, Redis or service discovery for this lab.

Test stopping each service independently, Kafka outage/recovery, REST dependency outage, replays and horizontal workers. Prove failures show pending/error with no invented financial results.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
