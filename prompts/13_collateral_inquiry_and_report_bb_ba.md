# Prompt 13: Collateral inquiry and report BB/BA

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

After an approved locate, implement Borrower collateral inquiry action. Build BB, correlate it with locate/MSLA/security/quantity, save audit/outbox and send via Kafka. Custodian parses/validates and authorizes it, obtains effective CASH haircut0 and separate coverage/rates from the selected agreement, requests a calculation if numerical collateral is required, and sends BA with the agreed profile/companion payload.

Return conditions version, price reference/time, amount, currency, haircut and required coverage distinctly. BA is a report/quote, not a collateral posting or loan approval. No invented eligible assets: supported future types remain in schema but current report offers CASH only.

Persist message audit rather than adding a quote table. Define quote/correlation reference, expiry and how AE later references the agreed terms; Custodian revalidates quote versions at approval. If state changed, reject/requote instead of silently altering borrower consent.

Test valid inquiry/report, invalid dictionary data, unavailable calculation/price source, expired locate, stale quote, forbidden collateral type, unauthorized MSLA and duplicate inquiry. A retry must correlate to the same logical inquiry.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
