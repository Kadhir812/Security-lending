# Prompt 26: End-to-end demo and acceptance

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Run the complete scenario matrix in docs/SCENARIOS.md against four running services, separate MySQL credentials and Kafka. Provide API requests/scripts or an HTTP collection, seed instructions and reproducible cleanup/reset for a disposable test environment. Use authenticated test users and service clients. Do not create a security bypass in the normal profile.

Trace borrower form selection -> preview -> locate -> BB/BA -> AE/AK -> collateral posting -> T+1 delivery -> holdings -> MTM -> margin top-up -> partial return/release -> recall remaining -> final return -> fees/rebate payment -> final collateral release -> CLOSED. Separately demonstrate AF/8 and f; they are conditional branches, not forced steps in every loan.

Exercise rejection, cancellation, partial settlement, overdue obligations, stale result, duplicate messages, two concurrent requests competing for inventory/credit, consumer crash, broker outage, REST outage, unauthorized org access and restart recovery. Verify business quantities/cash/credit/inventory and no double effects in databases through each service's own test credentials.

Report actual test evidence, remaining failures and schema changes. Count physical tables and confirm <=50 with framework metadata included. Document PDF deviations already agreed (MySQL, four services, borrower locate start and corrected AF/f), and mark implementation complete only for tests actually executed.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
