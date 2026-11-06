# Prompt 23: Billing, payment and reconciliation

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement fee/rebate payable calculation and Custodian obligation/payment workflows with existing lending_fee_payment and rebate_payment_history. A period can cover multiple daily result rows: snapshot the exact selected result references, correction chain, totals, payments already applied and currency. Use stable obligation_reference for a logical bill and unique payment_reference for each attempt/instalment.

Authenticate due-calculation requests; Calculation returns amount, payer/payee direction and provenance. Custodian verifies results and creates payment intent. Negative rebate uses reversed payer/payee and positive payment_amount. Zero-net due needs no fabricated zero payment row; expose it from the calculation/obligation result. Do not silently net unrelated counterparties/currencies.

Send payment instructions through an explicit simulated payment adapter and accept authenticated idempotent confirmations. Only confirmed transfer marks PAID. Failed attempts do not consume due amount; pending attempts reserve payable capacity so concurrent attempts cannot overpay. Serialize on local contract/obligation scope and account for all statuses. Duplicate external references are rejected.

Borrower reads obligations and receives status events. Test partial payment, retry after failure, concurrent payment, duplicate callback, signed rebate direction, corrections after a prior payment and remaining balance reconciliation.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
