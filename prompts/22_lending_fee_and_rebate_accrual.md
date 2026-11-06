# Prompt 22: Lending fee and rebate accrual

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement Custodian accrual scheduling and Calculation daily/period result generation. Define exact accrual start/end rules based on confirmed delivery/return timestamps, day-count convention, daily boundary timezone and valuation basis; document whether intraday changes are time-weighted or use an agreed end-of-day basis. Do not invent a universal market convention.

For a full configured day, fee=eligibleLoanValue*annualFeePct/100/dayCountDenominator; rebate=eligibleCashBalance*annualRebatePct/100/dayCountDenominator. Use signed rebates where agreed; positive vs negative determines payment direction later. Current quantities/balances alone cannot reconstruct history: build period inputs from immutable settlements/movements, posted/released collateral and their timestamps, historical prices and agreed contract terms.

Persist each daily accrual item in calculation_result with result_type, transaction/date/item key, amount/currency and full detail basis; no new accrual tables. Use deterministic logical period keys and guarded acceptance; a second run/correction must not cause double billing. Handle backdated confirmations and price corrections through superseding/adjustment runs.

Test ACT365/ACT360, leap-day date counts, partial/full returns, signed rebate, zero balances, multi-day period, rounding, duplicate schedules and historical correction.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
