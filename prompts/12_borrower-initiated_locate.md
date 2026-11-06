# Prompt 12: Borrower-initiated locate

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement POST /api/locates on Borrower and corresponding Custodian consumer/response. Borrower persists local locate intent and LOCATE_REQUESTED outbox atomically with shared locate reference. Custodian validates authenticated borrower, MSLA/mandate dates, asset type, security status, inventory availability and request expiry; saves received intent/decision and LOCATE_RESPONDED.

Define full approval, partial approval (explicit approved_quantity), rejection, expiry and cancellation. A locate is an availability response, not a settled loan or permanent inventory reservation. Inventory/credit must be rechecked at loan approval. Respect existing one-use locate link; concurrent use cannot create two accepted loan requests. Document whether partial locate approval changes the user's next confirmation step.

Borrower updates only response fields from Custodian events, guarding newer decisions. Show queued/sent versus decision state. Do not send AF as the initial locate; it is a separate scenario later.

Test expired/inactive MSLA, unsupported type, insufficient inventory, partial availability, repeated submission/delivery, wrong receiver/org, competing use, cancellation races and offline Custodian recovery.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
