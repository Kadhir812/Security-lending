# Schema validation

- security_market_db: 5 physical tables
- custodian_db: 28 physical tables
- borrower_db: 12 physical tables
- valuation_accrual_db: 5 physical tables

Total: 50, including all 12 messaging tables. No hidden projection or calendar
tables. These counts describe the checked-in fresh schema scripts.

`python db/validate_structure.py` performs static checks on those scripts:
FK target/order, FK type, index columns, parentheses, and table counts. It does
not connect to MySQL and does not validate migrations or live database state.
Run service persistence integration tests against disposable databases to
verify actual MySQL schemas.
