# Schema validation

- security_market_db: 5 physical tables
- custodian_db: 28 physical tables
- borrower_db: 12 physical tables
- valuation_accrual_db: 5 physical tables

Total: 50, including all 12 messaging tables. No hidden projection or calendar
tables. Local MySQL initially had 49: `credit_exposure_history` was missing
from Custodian. The existing additive migration table creation and the
Borrower `loan_contract_view.version` migration were applied after verifying
their preconditions. Current local counts match 5/28/12/5.

`python db/validate_structure.py` passed its local FK target/order, FK type,
index-column, parenthesis, and table-count checks. The four fresh schema scripts
were compared against the configured local service databases; each database
connection succeeded using that service's own configured credentials. This is
not a full SQL parser or application persistence/integration test.
