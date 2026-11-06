# Prompt 16: Existing-order status AF and 8

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement optional existing-order monitoring independently of locate initiation. Borrower maintains borrower_order with real seeded demo orders or an authenticated order adapter; order reference, borrower/org, ISIN, side, quantities, status and version are validated. Do not substitute loan_contract as an order ledger.

Custodian sends an authorized AF with agreed scope/filter and request reference. Borrower selects only permitted matching orders and returns the appropriate ExecutionReport8 messages with correlated request, per-order status, fill/leaves quantities and required completion/count semantics of the chosen dictionary/profile. Handle cancelled orders with leaves0 even when unfilled quantity remains; do not derive leaves blindly as orderQty-filledQty for every status.

Agree and implement the no-matching-order response according to the profile. Audit requests/replies in existing message_log/outbox; no extra report table. Duplicate AF must not mutate orders and responses must not masquerade as new execution fills.

Test multiple orders, filters, zero matches, filled/partial/cancelled states, repeated AF, scope violation and malformed FIX.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
