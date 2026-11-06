# Prompt 17: Instrument status f

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement the conditional instrument-status flow. An authorized Borrower source may report a real HALTED/RESUMED-type observation through f per the agreed FIX profile. Custodian validates source, timestamp, ISIN and message before forwarding an authorized update command to Security & Market Data. Market remains the sole authoritative instrument-status writer.

Implement source trust, duplicate/outdated observation rejection and conflict policy; arbitrary borrowers cannot globally halt a security. Use the trading-status field patch from prompt01 if necessary. Keep registration ACTIVE/INACTIVE distinct. An instrument halt can block new borrowing according to policy, but must not erase existing loans or falsely mark them recalled/returned.

Do not use f for Allocated/OnLoan/Recalled; those are application business events. This branch is optional/conditional and is not a mandatory final message of every loan.

Test authorized halt/resume, untrusted source, old timestamp, duplicate observation and existing-loan preservation.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
