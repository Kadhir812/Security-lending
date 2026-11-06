# Prompt 10: Borrower forms and previews

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement Borrower-facing endpoints to search securities through Market REST, list scoped MSLA/lender choices through Custodian REST and retrieve contract/obligation views without local projection tables. Derive custodian route from the selected authorized mandate/MSLA, never accept an arbitrary receiver supplied by the browser.

Validate selected ISIN, allowed asset type, positive whole-unit quantity and requested dates. Request collateral/fee preview from Calculation using authorized agreement terms. Clearly mark it as an estimate with input/price times and expiry; it cannot reserve inventory, consume credit or create a contract.

Persist submitted intent only when the user confirms. Add business-reference/idempotency handling using existing unique request references and a body fingerprint stored in audit payload where necessary. A repeated client reference with changed parameters must conflict. Surface REST timeouts, unavailable data and stale preview without inventing eligibility or prices.

Provide API examples matching a form-based UI: choose MSLA, search security, enter quantity, preview, submit. The UI must not ask users to choose raw FIX codes.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
