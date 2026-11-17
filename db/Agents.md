Implement the securities-lending backend incrementally, one complete scenario at a time.

Architecture and stack:
- Security & Market Data, Custodian, Borrower, Valuation & Accrual services.
- Java 21, installed Spring Boot 4 version, Maven, MySQL, and Kafka.
- Each service accesses only its own database; no shared JPA entities.
- The supplied 48-table schema is authoritative. Stay within 50 physical tables
  across all databases, counting repeated inbox/outbox tables.
- Inspect the existing project, schema, and docs/PROGRESS.md before coding.
- Preserve table/column names and exact SQL enum values.
- Explain blocking schema gaps and propose the smallest necessary change.

Package structure—create packages only when needed:
- controller, service, dto/request, dto/response, repository, entity
- kafka/config, kafka/producer, kafka/consumer, kafka/event
- kafka/outbox, kafka/inbox, security, exception, config, client, mapper, enums
- quickfix/ belongs only in Borrower and Custodian.
- calculator/ belongs only in Valuation & Accrual.
- Business schedulers belong in Custodian initially.
- Outbox polling/retry code belongs under kafka/outbox/.
- Security & Market Data uses REST only; do not add Kafka infrastructure there.

Code quality:
- Write readable, maintainable code that another developer can understand quickly.
- Use descriptive domain names, focused methods, and straightforward control flow.
- Prefer explicit, simple logic over clever shortcuts or unnecessary abstractions.
- Keep controllers thin, business logic in services, and persistence in repositories.
- Use DTOs at API boundaries; do not expose JPA entities directly.
- Avoid duplicated logic, speculative utilities, dead code, and unused configuration.
- Use explicit imports, consistent formatting, and the existing project conventions.
- Comment business reasons and non-obvious decisions; avoid narrating obvious code.
- Return clear validation errors; never swallow exceptions or log sensitive payloads.
- Use Lombok @Data and @Builder consistently for entities and data/DTO classes.
- Keep a JPA no-argument constructor and builder-compatible constructors.
- Use @Builder.Default for initialized defaults.
- Use @RequiredArgsConstructor for constructor-injected components where suitable.
- Do not place data-object annotations on stateless controllers or services.
- Rely on @Data by default; customize equality/string methods only when necessary.
- Exclude relationships and sensitive/large fields from toString where needed.
- Put each enum in its own file under its service's enums/ package.

Implementation and financial rules:
- Implement only the current scenario and preserve previously completed scenarios.
- Create only the classes/configuration required; do not implement future scenarios.
- Use REST for immediate queries and Kafka for durable workflows.
- Commit business changes and outgoing outbox events in one local transaction.
- Prevent duplicate effects through transactional inbox/idempotency handling.
- Use BigDecimal for money, with explicit rounding and day-count rules.
- Borrower/Custodian own quantity checks and workflow validation.
- Valuation & Accrual calculates; Custodian decides and applies results.
- Keep MSLA default_rules as backend-validated JSON.
- Keep eligible_securities as JSON containing allowed asset types.
- Reference selected securities by ISIN.
- Enforce CASH collateral with 0% haircut; preserve future collateral enum values.
- Keep coverage percentage separate from haircut.
- Create contract collateral_terms as an approval-time JSON snapshot.

After each scenario:
- List changed files and explain the flow simply.
- Provide representative requests/events.
- Run relevant success, rejection, and duplicate-processing tests.
- Report actual build/test results and environment blockers; never invent success.
- Update docs/PROGRESS.md with completed work, checks, and remaining gaps.
- Keep each scenario's documentation update and completion report within 60 lines.