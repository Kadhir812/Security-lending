# Prompt 21: Intraday recall and maturity

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement scoped Custodian POST /api/contracts/{reference}/recalls for lender/mandate-authorized recall. Save recall_request and Kafka notice atomically, with amount/quantity, reason and deadline from the agreed recall calendar/cutoff policy. Recall is independent of collateral sufficiency. Do not hardcode all recall deadlines as trade T+1.

Borrower receives notice, reads current obligations and submits return processing. Reuse confirmed-return workflow from prompt20; apply confirmed returned quantities to the correct recall without double counting a simultaneous voluntary return. Support partial recall, partial fulfilment, overdue/default/escalation, cancellation before execution and conflicting recall attempts. Keep loan exposure/fees/MTM active until actual returns occur.

Add maturity-date scheduler using the configured policy; correlate with any open recall rather than issuing duplicates. Derive pending quantity from active instructions and confirmed balances, not notification status alone. Cancellation cannot undo already confirmed settlement.

Test intraday recall, weekend/holiday deadline, no available borrower holdings, partial fulfilment, overdue notice, duplicate recall, return races and full fulfilment.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
