# Prompt 15: Collateral posting and T+1 delivery

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement CASH collateral posting workflow, settlement_instruction creation and custodian processing. Backend-owned payment/custody simulation adapters produce confirmation references; external callbacks are authenticated. A click or due date alone is not settlement.

Compute T+1 using versioned configured calendar/timezone/cutoff and record the calendar version; missing date coverage fails explicitly. Track collateral and security legs independently with their existing instruction types. Verify confirmed collateral suffices for the proposed delivery, and preserve agreed sequencing policy. Support partial delivery with distinct instruction references; if schema needs a small field/index patch, document it instead of inventing a settlement history table.

On each confirmed security delivery, atomically record instruction/movement, consume reservation, move lender reserved quantity to on-loan, update delivered/outstanding contract quantity and reserved/used credit correctly, write history/outbox. Borrower changes holdings only from authorized confirmed settlement events. Ensure cumulative delivered quantity<=contract quantity. No double counting if security delivery and collateral callbacks arrive out of order.

Test business-day/weekend/holiday/cutoff dates, insufficient collateral, failed/pending legs, partial delivery, duplicate confirmations, reordered confirmations, restart and cancellation races. Expose reconciliation for an external confirmation whose local persistence initially failed.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
