# Prompt 24: Closure, cancellations and exception recovery

Read and follow prompts/00_MASTER_INSTRUCTIONS.md first.

Implement terminal lifecycle gates: no outstanding securities, no pending delivery/return/release instructions, collateral release resolved and required financial obligations settled or explicitly resolved under agreed policy. Distinguish RETURNED from CLOSED. Record actor/reason/history and emit closure once.

Implement pre-settlement cancellation/rejection and reservation expiry: atomically release unconsumed inventory and reserved credit, prevent future delivery, preserve any confirmed legs and create recovery tasks/events where needed. A timeout must not imply an external settlement did not occur. Define manual reconciliation via existing audit/messages and scoped administrative endpoints without adding tables.

Expose failed settlement, breached margin, overdue recall and DEFAULTED states without automatically deleting records or writing off balances. Define remediation/authorization rules and protect immutable history.

Test cancel-vs-delivery race, expiry after partial consumption, full return with unpaid fee, pending release blocking closure, duplicate closure command, missed external confirmation and restart recovery.

Finish by updating docs/PROGRESS.md with evidence and the next dependency.
