# Prompt 18: Intraday MTM and coverage

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement Custodian scheduled and price-triggered MTM selection for active settled exposure. Use configured MSLA interval and a deterministic window/reference so a scheduler and price event do not accidentally create duplicate logical obligations. Capture contract/outstanding quantity, collateral balances/versions, agreement conditions and as-of time in the calculation request; publish via outbox.

Calculation uses exact as-of price, persists immutable snapshots and produces loan value, adjusted collateral, required collateral and coverage. Custodian validates source, purpose, contract/collateral/terms versions and price freshness before applying the result to latest_* columns and accepted_valuation_snapshot; stale result is rejected/requeued. Updates do not double-apply economic effects.

No collateral means coverage0 when loanValue>0; loanValue0 means undefined coverage. Pending un-delivered commitments require their separate reservation protection and must not cause premature collateral release when settled exposure is zero. Recalled but outstanding loans continue MTM.

Test out-of-order results, stale collateral version, zero/partial/full outstanding, price outage, rapid price changes, duplicate scheduler delivery and concurrent settlement.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
