# Indian development seed (REST-only Stage 3A)

Retrieved 2026-11-07 from the official NSE reference downloads linked from
https://www.nseindia.com/static/market-data/securities-available-for-trading

- Equities: https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv
- ETFs: https://nsearchives.nseindia.com/content/equities/eq_etfseclist.csv

Exactly 90 equities (first 90 EQ-series INE entries in the downloaded order)
and 10 ETFs (first 10 ETF-list entries). Symbols, security names (including NSE's
abbreviated ETF SecurityName labels), and ISINs come directly from these files.
All ISIN checksums were checked during dataset preparation. These are reference
identities, not a claim about current trading eligibility or lending eligibility.

All prices are deliberately SIMULATED: INR 100 + 7 * zero-based row index,
source DEMO_SIMULATED, fixed timestamp 2026-01-02T10:00:00Z. They are not live
quotes. UUID v5 references derive from ISIN, timestamp and source.

Enable only deliberately: MARKET_SEED_ENABLED=true. Default is false.
The loader inserts missing identities and observations in one local transaction.
Existing security details and price rows are never overwritten. Existing
securities with a different currency do not receive an INR demo observation.
Re-running adds no duplicates. Disable the flag after development setup.

Downloaded full-file SHA-256 values (source evidence):
- EQUITY_L.csv: `da7eb83cf21156cca3a8bcc5b94ea55f825ff7348e02c89abc51658788361fa5`
- eq_etfseclist.csv: `3c802209ee0e2becfc356253ba2b48ab4a2743cc680283a4f575452b07b97e9a`
