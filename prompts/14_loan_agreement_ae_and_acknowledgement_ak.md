# Prompt 14: Loan agreement AE and acknowledgement AK

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement Borrower confirmation of agreed terms, local loan_request and AE publication with unique requestReference. Custodian validates AE/profile and its referenced locate/quote; validates positive quantity, date/calendar, security, agreement, effective fee/coverage/CASH policy and credit/inventory. Obtain an authoritative approval calculation using requested quantity, not zero unsettled quantity.

Perform remote reads/calculation outside the locked DB transaction. Then lock/version-check local agreement, credit, inventory and locate; reject/recalculate on changes. Atomically create one loan_contract per request, reserve inventory and credit, mark locate use and request decision, snapshot collateral_terms and calculation evidence, write status history and AK outbox. Reservation must expire/release safely on failed/cancelled workflows.

AK accepts/rejects the reported trade per profile. Borrower updates request state; do NOT increase settled borrower_inventory merely on AK. Initial contract remains approved/pending collateral/settlement. Never exceed inventory or credit under concurrent approvals.

Test two requests competing for the last available shares/credit, duplicate AE, response loss, stale rates, invalid locate reuse, approval timeout, expired reservation and rejection response. Ensure retries cannot create a second contract.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
