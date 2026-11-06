# Prompt 09: Valuation and accrual engine

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement pure BigDecimal calculation functions and REST/job orchestration. calculation_run stores immutable inputs, exact selected prices, rule parameters/version/digest; calculation_result stores individual item results. Enforce requester/requestReference uniqueness with hash comparison. Same request+same payload returns prior run; changed payload under same key is a conflict. Corrections create new superseding results.

Differentiate approval exposure (requested/approved quantity and pending obligations) from settled MTM exposure (delivered minus returned quantity); zero delivered quantity must not produce zero approval collateral. Define pending exposure and release safeguards explicitly. Require same currency CASH and 0% haircut for current workflow. Coverage=adjustedCollateral/loanValue*100; zero denominator returns undefined/NULL.

Implement required collateral, shortfall/excess, threshold/minimum-transfer handling, permitted release estimate, daily fee and signed rebate accrual, period totals and payment-due adjustments. Use configured day-count, rate-as-percent conversion, rounding and accrual interval semantics. Keep actual business decisions outside the engine.

Background jobs acquire recoverable leases, persist results plus outbox atomically and never recalculate a retry using a newer price. Restrict authoritative runs to authenticated Custodian service; borrower previews are marked PREVIEW. Test exact numbers, zero quantities, stale prices, corrections, duplicate job keys and invalid currencies.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
