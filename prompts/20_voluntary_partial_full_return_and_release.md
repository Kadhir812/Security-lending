# Prompt 20: Voluntary partial/full return and release

Read and follow prompts/Agents.md first.

Implement borrower return request against outstanding quantity using application events. Custodian validates current balance and existing pending return instructions so concurrent requests cannot return more than outstanding. Create security-return instructions; no balance changes on request alone.

On authenticated confirmed return, atomically update instruction/movement, contract returned quantity, lender on-loan availability and credit according to the explicit exposure policy. Emit confirmed balance event; Borrower updates holdings idempotently. Preserve partial return with remaining active exposure and continue MTM/accrual on the applicable remaining basis.

Request collateral-release calculation using current balances, pending obligations/reservations and unresolved financial obligations. Calculation gives a ceiling; Custodian authorizes the release and creates a release instruction. Mark released cash only after confirmation. Maintain remaining coverage and prevent concurrent over-release. A full return does not automatically mean all fees/rebates paid or contract CLOSED.

Test partial40 of100, full remaining60, excess requested quantity, return/release failure, duplicate/out-of-order callbacks, pending deliveries, pending recall and concurrent releases.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
