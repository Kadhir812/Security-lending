# Validation

- security_market_db: 5 physical tables
- custodian_db: 27 physical tables
- borrower_db: 11 physical tables
- valuation_accrual_db: 5 physical tables

Total: 48, including all 12 messaging tables. No hidden projection or calendar tables. Static structural checker is included. No MySQL server is installed here; runtime SQL execution and integration tests remain outstanding.

Executed validate_structure.py successfully: local FK target existence/order, matching FK column types, index columns, balanced parentheses and table count. Not a complete SQL parser or runtime test.
