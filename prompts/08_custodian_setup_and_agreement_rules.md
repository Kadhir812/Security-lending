# Prompt 08: Custodian setup and agreement rules

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement organization registration, local staff profiles/roles, lender-custodian mandates, MSLA, inventory, credit-limit management, haircut rules and lending fee rules. Validate organization types, active dates and borrower/lender/custodian relationships. Record credit limit changes with actor/reason/history.

MSLA eligible_securities JSON permits asset types, not security IDs. Build/validate default_rules JSON against a documented typed backend contract: thresholds/minimum transfer, rounding, scheduling/payment rules and permitted defaults. Required missing rules must not silently obtain invented financial values. Keep fixed agreement terms and versioned effective rules.

Current backend rejects non-CASH selection and nonzero effective CASH haircut. Keep full supported ENUM/multiasset columns unchanged. Coverage is separate; default105% is a demo default, not universal market law. Resolve fee rules by security specificity/effective dates; reject ambiguous overlapping active rules.

Validate credit used+reserved+new against limit atomically; reconcile reductions below current exposure according to an explicit rule. Seed one lender, custodian, borrower, mandate, active MSLA, fee/haircut and inventory for the demo. Authorize all administrative writes and emit owner-versioned events where required.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
