# Prompt 19: Margin call and top-up

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Use accepted MTM results to determine margin-call amount from agreed threshold/minimum-transfer/rounding rules. Calculation computes shortfall; Custodian decides issuance. Create margin_call, calculation_snapshot, transaction log and outgoing event atomically. Enforce the agreed one-active-call policy by locking the contract; define whether later MTM updates the call or supersedes it, never issue conflicting duplicate obligations.

Borrower retrieves call details through scoped REST and submits top-up intent through Kafka. Custodian creates collateral-transfer settlement instructions. Only confirmed posting increases collateral and satisfies/reduces the call. Track partial fulfilment, due deadline, breach/failure and cancellation with audit. Revalue after confirmation when appropriate; a top-up based on old prices may not resolve a newer shortfall.

A margin call is not automatically a security recall. Escalation/default requires explicit configured workflow. Prevent applying one settlement confirmation to multiple calls or contracts.

Test exact coverage boundary, threshold/MTA, partial top-up, changed price, duplicate confirmation, past deadline, unavailable borrower, unauthorized payer and concurrent top-ups.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
