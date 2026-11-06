# Prompt 02: Complete the service foundations

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Use the supplied root and four child POMs; keep four deployable JARs and no service-to-service Maven dependencies. Verify Java 21/Maven and resolve the pinned dependencies. Do not introduce Boot 4 without changing the agreed requirement.

Implement common per-service exception translation using ProblemDetail, request validation, correlation IDs, structured logging, Clock and service URL/timeout configuration. Use feature packages api/application/domain/persistence. Controllers accept DTOs, not JPA entities; domain logic is not embedded in controllers. Configure bounded synchronous RestClient calls and typed failure outcomes.

Add a minimal health check in each service and application-start tests once DB/auth configuration is available. Root mvn verify must build all modules; mvn -pl borrower-service package must build Borrower without packaging other services. Explain publishing the parent POM for independent CI. Protect actuator details and metrics.

The current application classes/package-info files are scaffolding; replace placeholders as work is implemented. Do not claim the four running ports alone demonstrate complete microservices.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
