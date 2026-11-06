# Prompt 11: QuickFIX adapters and agreed lab profile

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement separate Borrower and Custodian FIX adapters using the pinned quickfixj-core and quickfixj-messages-fix44. Read the actual dictionary/API from resolved dependencies. Define a checked-in profile specifying each message's purpose, required standard fields, agreed extensions/JSON companion fields, sender/target IDs, request correlation and rejection behavior.

Implement builders/parsers/validation for AF, ExecutionReport8, BB, BA, AE, AK and f. AF=existing-order mass status;8=response. AK is TradeCaptureReportAck with business acceptance/rejection, not settlement confirmation. f is instrument status. Do not repurpose standard tag meanings to carry MSLA terms. BB/BA haircuts/eligibility must have explicit mapping and typed validation.

Use actual SOH on the wire, correct BodyLength and CheckSum, and round-trip tests. Avoid hand-concatenating FIX. Define offline Kafka header treatment explicitly; do not fake a TCP session by calling Session.sendToTarget. Validate raw FIX against the envelope's declared type and organization routing. Persist original payload plus raw FIX for audit without secrets.

Test each message type, missing required tags, bad checksum/type, malformed repeating groups, unsupported extensions and wrong sender. Preserve codes and corrected scenarios, not the original PDF's incorrect AF/f semantics.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
