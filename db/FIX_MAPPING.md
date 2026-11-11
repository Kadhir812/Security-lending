# FIX 4.4 Borrower-Custodian Contract

## Scope and compatibility

This contract covers Borrower-initiated loan request submission (`U1`),
collateral inquiry (`BB`), the Custodian collateral report (`BA`), the
Custodian approval confirmation (`AK`), and initial securities settlement
confirmation (`U2`). The transport is a FIX-encoded business message in the existing Kafka event
envelope; there is no socket FIX session. The version is FIX 4.4, parsed with
QuickFIX/J 3.0.2. Both services use identical, project-specific
`FIX44_SL.xml` dictionaries. `U1` is a project-defined application message
type, not a standard loan-request type. U2 is also a project-defined
application message; FIX 4.4 does not define this lending settlement profile.

The existing REST API remains `POST /api/loan-requests` in Borrower and
`GET /api/loan-requests/{requestReference}` in Custodian. The current request
validation and Custodian business checks remain authoritative.

## Event and party mapping

| Business action | Sender -> receiver | Existing event and code |
|---|---|---|
| Submit loan/security request | Borrower organization -> Custodian organization | `LOAN_REQUEST_SUBMITTED`; Borrower `LoanRequestWriter` / `OutboxPublisher`; Custodian `InboxProcessor` / `LoanRequestSubmittedHandler` |
| Request collateral inquiry for an existing loan | Borrower organization -> Custodian organization | `COLLATERAL_INQUIRY_SUBMITTED`; Borrower `CollateralInquiryService` / `OutboxPublisher`; Custodian `InboxProcessor` / `CollateralInquirySubmittedHandler` |
| Return collateral report for a valid inquiry | Custodian organization -> Borrower organization | `COLLATERAL_REPORT_ISSUED`; Custodian `CollateralInquirySubmittedHandler` / `OutgoingEventService`; Borrower `InboxProcessor` / `CollateralReportIssuedHandler` |
| Confirm an approved loan request and deliver its existing contract snapshot | Custodian organization -> Borrower organization | `LOAN_REQUEST_DECISION` with FIX `AK`; Custodian `LoanDecisionCommitter` / `OutgoingEventService`; Borrower `InboxProcessor` / `LoanRequestDecisionHandler` |
| Confirm initial securities delivery after authorized settlement commits | Custodian organization -> Borrower organization | `SETTLEMENT_INSTRUCTION_UPDATED`; Custodian `InitialSettlementService` / `OutgoingEventService`; Borrower `InboxProcessor` / `SettlementInstructionUpdatedHandler` |

The request reference is the existing REST idempotency key and Kafka
correlation/aggregate reference. Kafka `eventId`, event type, schema version,
producer/destination, parties, versions, occurrence time, and topic metadata
remain in the JSON envelope. `payload` contains only `fixMessage` for new
events; request business fields are not duplicated in JSON.

## Message fields

SOH (`0x01`) separates fields on the wire. The local dictionary defines this
message's required fields and data types; standard tag numbers retain their
FIX 4.4 base meanings, with the application-specific use described below.

| Tag | FIX field / type | Required | Contract value, units, and meaning |
|---:|---|:---:|---|
| 8 | BeginString / STRING | Yes | `FIX.4.4` |
| 9 | BodyLength / LENGTH | Yes | Calculated FIX body byte length |
| 35 | MsgType / STRING | Yes | `U1` — project-defined Borrower loan request |
| 49 | SenderCompID / STRING | Yes | Borrower `organizationReference`; must equal the JSON envelope sender |
| 56 | TargetCompID / STRING | Yes | Custodian `organizationReference`; must equal the JSON envelope receiver |
| 34 | MsgSeqNum / SEQNUM | Yes | No-session convention: `aggregateVersion + 1`; initial request is `1`, not a reusable FIX session sequence |
| 52 | SendingTime / UTCTIMESTAMP | Yes | Envelope `occurredAt`, UTC, formatted to milliseconds |
| 9001 | RequestReference / STRING | Yes | Project extension; request reference and idempotency key |
| 9002 | MslaReference / STRING | Yes | Project extension; existing MSLA reference |
| 48 | SecurityID / STRING | Yes | ISIN, 12 uppercase alphanumeric characters |
| 22 | IDSource / STRING | Yes | `4` (`ISIN`) |
| 38 | OrderQty / QTY | Yes | Positive whole number of shares; fractional quantities are rejected |
| 15 | Currency / CURRENCY | Yes | Three-letter uppercase settlement currency code |
| 64 | SettlDate / LOCALMKTDATE | Yes | Requested settlement date, `YYYYMMDD` |
| 9003 | SubmitterIssuer / STRING | Yes | Project extension; authenticated submitter token issuer |
| 9004 | SubmitterSubject / STRING | Yes | Project extension; authenticated submitter subject |
| 9005 | LocateReference / STRING | No | Project extension; existing locate reference when supplied |
| 10 | CheckSum / STRING | Yes | Calculated three-digit FIX checksum |

Tags `9001`–`9005` and the `U1` message definition are project extensions and
are present in both service dictionaries. Standard `35=AF` is not used:
FIX 4.4 assigns AF to `OrderMassStatusRequest`, not a securities-lending loan
request.

## Validation, correlation, and retry behavior

- Borrower keeps its existing authorization and business validation. Saving
  the request and its FIX-bearing outbox event uses the existing local
  transaction; no table or migration is introduced.
- Before publishing, the outbox publisher validates the FIX dictionary,
  framing (`8`, `9`, and `10`), request correlation, parties, sequence
  convention, and timestamp. It sends through the existing loan-request Kafka
  route.
- Custodian validates FIX version/type, dictionary, framing/checksum, field
  types, identifier/date/quantity formats, and agreement between FIX headers,
  envelope routing, and event correlation before translating to the existing
  incoming DTO. Existing membership, party, MSLA/mandate, date, security,
  currency, and locate checks still decide acceptance.
- The existing inbox transaction and `eventId` deduplication are unchanged.
  Replays of the same event do not repeat persistence; a reused event ID with
  different content is rejected. Request-reference idempotency additionally
  rejects a reference reused with different business values.
- A queued legacy JSON outbox payload is converted to U1 before its existing
  event is published; its event ID is retained and only FIX business content
  is sent. Already-published legacy JSON request events are accepted by the
  Custodian compatibility path. No new JSON business event is emitted.
- Invalid FIX or envelope mismatches are rejected through the existing inbox
  error handling. Valid FIX that fails existing business checks remains a
  business rejection; this integration does not approve or alter requests.

## FIX 4.4 BB CollateralInquiry

BB is the standard FIX 4.4 `CollateralInquiry` message type. The implementation
uses the QuickFIX/J 3.0.2 FIX 4.4 dictionary as its reference. In that standard
dictionary, `CollInquiryID(909)` and the qualifier group
`NoCollInquiryQualifier(938)` / `CollInquiryQualifier(896)` are optional; the
standard header and trailer fields remain required. This project requires
`CollInquiryID` and a request/MSLA link in its BB profile. The optional
qualifier group is preserved in both dictionaries but not sent by this
request-scoped inquiry action.

| Tag | FIX field / type | Required in this profile | Contract value |
|---:|---|:---:|---|
| 35 | MsgType / STRING | Yes | `BB` — standard FIX 4.4 `CollateralInquiry` |
| 49 | SenderCompID / STRING | Yes | Authenticated Borrower `organizationReference`; must match the JSON envelope sender |
| 56 | TargetCompID / STRING | Yes | Existing request's Custodian `organizationReference`; must match the JSON envelope receiver |
| 34 | MsgSeqNum / SEQNUM | Yes | No-session convention: `aggregateVersion + 1`; this isolated inquiry starts at `1` |
| 52 | SendingTime / UTCTIMESTAMP | Yes | Envelope `occurredAt`, UTC, milliseconds |
| 909 | CollInquiryID / STRING | Project-required | Server-generated UUID; also the envelope correlation and aggregate reference |
| 938 | NoCollInquiryQualifier / NUMINGROUP | No | Standard optional FIX qualifier group |
| 896 | CollInquiryQualifier / INT | No | Standard qualifier enum `0`–`7`, nested under tag 938; omitted by this action |
| 9001 | RequestReference / STRING | Yes | Lending extension reused from U1; links to the pre-existing Borrower loan request |
| 9002 | MslaReference / STRING | Yes | Lending extension reused from U1; must match that loan request's MSLA |
| 8, 9, 10 | BeginString, BodyLength, CheckSum | Yes | Standard FIX 4.4 framing; checksum and body length are validated |

`9001` and `9002` are project extensions (not standard FIX collateral fields).
They avoid introducing a second representation for existing loan/MSLA
identifiers. The BB message carries no collateral amounts, balances, or
instruction fields.

The Borrower REST action is
`POST /api/loan-requests/{requestReference}/collateral-inquiries`; it uses the
existing authenticated Trader authorization and requires the request to belong
to that Borrower. Its `202 Accepted` response includes the generated inquiry ID
and `QUEUED` status. The event is atomically saved to the existing outbox and
sent on `sec-lending-requests`; the envelope aggregate/correlation reference is
the inquiry ID, while the FIX extensions link it to the request and MSLA.

Custodian validates the FIX dictionary, frame/checksum, BB type, UUID,
envelope correlation, parties, and that the referenced request exists with the
same MSLA. It verifies the parties against the request's persisted MSLA and
mandate. The existing inbox transaction persists the event payload and
deduplicates by `eventId`; no inquiry table is added. Receipt does not write
loan, collateral, reservation, or balance state and cannot approve a loan.
The BB receipt itself does not mutate lending or collateral state. The BA
response contract and handling are specified in the next section.

## FIX 4.4 BA CollateralReport

BA is the standard FIX 4.4 `CollateralReport` message type. QuickFIX/J 3.0.2's
FIX 4.4 dictionary defines the standard `CollRptID(908)`, `CollInquiryID(909)`,
`CollStatus(910)`, and `AgreementCurrency(918)` fields. This project profile
requires all four; tag 909 is required for strict BB-to-BA correlation even
where the base dictionary permits it to be absent. Both custom dictionaries
include the same BA message profile and lending extensions.

| Tag | FIX field / type | Required in this profile | Contract value, units, and meaning |
|---:|---|:---:|---|
| 35 | MsgType / STRING | Yes | `BA` — standard FIX 4.4 `CollateralReport` |
| 49 | SenderCompID / STRING | Yes | Custodian `organizationReference`; must equal the Kafka envelope sender |
| 56 | TargetCompID / STRING | Yes | Borrower `organizationReference`; must equal the Kafka envelope receiver |
| 34 | MsgSeqNum / SEQNUM | Yes | No-session convention: `aggregateVersion + 1`; initial response is `1` |
| 52 | SendingTime / UTCTIMESTAMP | Yes | Kafka envelope `occurredAt`, UTC, milliseconds |
| 908 | CollRptID / STRING | Yes | Standard report identifier; deterministic UUID derived from the inquiry ID for response idempotency |
| 909 | CollInquiryID / STRING | Yes | Standard inquiry identifier; equals the original BB ID and envelope `correlationId` |
| 910 | CollStatus / INT | Yes | Standard status `0` (`UNASSIGNED`); this report describes terms, not received/assigned collateral |
| 918 | AgreementCurrency / CURRENCY | Yes | Standard MSLA/base currency; must match the request's currency |
| 9001 | RequestReference / STRING | Yes | Lending extension reused from U1/BB; equals the Kafka aggregate reference |
| 9002 | MslaReference / STRING | Yes | Lending extension reused from U1/BB; links the report to the authoritative MSLA |
| 9010 | LendingCollateralType / STRING | Yes | Lending extension; `CASH`, sourced from the authoritative agreement |
| 9011 | LendingCashHaircutPercentage / PERCENTAGE | Yes | Lending extension; numeric percent on a 0–100 scale, authoritative effective CASH haircut (`0`) |
| 9012 | LendingCollateralCoveragePercentage / PERCENTAGE | Yes | Lending extension; numeric percent, at least 100 per the existing MSLA constraint, kept separate from haircut and sourced from the agreement |
| 8, 9, 10 | BeginString, BodyLength, CheckSum | Yes | Standard FIX 4.4 framing; dictionary, byte length, and checksum are validated |

Tags `9010`–`9012` are project-specific extensions, not standard definitions
of collateral terms. The Custodian validates the current effective CASH
haircut is unambiguous and zero before producing BA. It does not derive an
amount, call valuation, or include price/as-of fields; consequently, no
indicative collateral amount is reported. BA does not approve the request,
mark collateral received/assigned, reserve assets, or change balances.

The Custodian creates BA inside the existing BB inbox transaction. Its
deterministic report/event ID and inquiry correlation prevent a second logical
response for duplicate inquiries, while the existing inbox deduplication
prevents reprocessing the same event ID. The Borrower validates parties,
request/MSLA/currency, and the original outbound BB record before the existing
inbox persists the FIX-bearing envelope. The authenticated read action
`GET /api/loan-requests/{requestReference}/collateral-reports` checks request
ownership and reads processed BA records from the existing inbox; no table is
added.

Example (`|` represents SOH; framing values are computed):

```text
8=FIX.4.4|9=<computed>|35=BA|49=CUSTODIAN-ORG|56=BORROWER-ORG|34=1|52=20261111-06:10:00.123|908=12f6c65f-603e-5aef-8c4a-416bd8d9176b|909=6f4f73de-cb44-47d2-a7f1-ec4a5e381b08|910=0|918=INR|9001=LR-EXAMPLE-001|9002=MSLA-EXAMPLE-001|9010=CASH|9011=0|9012=105|10=<computed>
```

## FIX 4.4 AK loan approval confirmation

FIX 4.4 assigns `AK` to the standard `Confirmation` message. It is not a
standard loan-approval message. This project explicitly profiles AK as an
approval confirmation over Kafka; that application meaning and the custom
field profile are project extensions and require counterpart agreement.
The dictionary preserves the standard AK confirmation identifiers/status and
timestamps, and requires every contract/valuation snapshot field needed by
the existing Borrower decision handler. AE remains pending.

The envelope remains `LOAN_REQUEST_DECISION` with its original event ID,
request correlation/aggregate reference, aggregate version, parties, and
occurrence time. Only approved decisions use a FIX-only payload. Rejections
continue using the existing JSON decision payload and handler path. A
previously queued JSON approval event remains readable by the Borrower for
compatibility. The Custodian serializes FIX before `OutgoingEventService`
enqueues the event in the same approval transaction; no parallel JSON business
payload is published.

| Tag | FIX field / type | Required | Contract value and meaning |
|---:|---|:---:|---|
| 664 | ConfirmID / STRING | Yes | Standard AK confirmation ID; existing contract `transactionReference` |
| 666 | ConfirmTransType / INT | Yes | Standard enum `0` (`NEW`) |
| 773 | ConfirmType / INT | Yes | Standard enum `2` (`CONFIRMATION`) |
| 665 | ConfirmStatus / INT | Yes | Standard enum `4` (`CONFIRMED`); the project maps this to decision `APPROVED` |
| 60 | TransactTime / UTCTIMESTAMP | Yes | Existing decision `respondedAt`, UTC milliseconds |
| 75 | TradeDate / LOCALMKTDATE | Yes | Existing contract trade date |
| 9001 | RequestReference / STRING | Yes | Project extension; must equal envelope correlation and aggregate reference |
| 9002 | MslaReference / STRING | Yes | Project extension; links this decision to the originating MSLA |
| 9100 | DecisionVersion / INT | Yes | Project extension; existing decision/aggregate version |
| 9101 | DecisionStatus / STRING | Yes | Project extension; `APPROVED` |
| 9102 | SecurityISIN / STRING | Yes | Project extension; contract ISIN |
| 9103 | ContractQuantity / QTY | Yes | Project extension; whole-share contract quantity |
| 9104 | ContractCurrency / CURRENCY | Yes | Project extension; settlement currency |
| 9105 | AgreedAnnualFeePercentage / PERCENTAGE | Yes | Project extension; agreed annual fee percent |
| 9106 | DayCountBasis / STRING | Yes | Project extension; contract day-count convention |
| 9107 | ContractCoveragePercentage / PERCENTAGE | Yes | Project extension; agreed collateral coverage percent |
| 9108 | CollateralType / STRING | Yes | Project extension; existing collateral type |
| 9109 | CollateralCurrency / CURRENCY | Yes | Project extension; collateral terms currency |
| 9110 | CollateralHaircutID / INT | Yes | Project extension; effective haircut rule identifier |
| 9111 | CollateralHaircutPercentage / PERCENTAGE | Yes | Project extension; collateral terms haircut |
| 9112 | CollateralTermsCoveragePercentage / PERCENTAGE | Yes | Project extension; collateral terms coverage |
| 9113 | CollateralRequiredAmount / AMT | Yes | Project extension; collateral terms required amount |
| 9114 | CollateralLoanMarketValue / AMT | Yes | Project extension; collateral terms loan value |
| 9115 | CollateralValuationReference / STRING | Yes | Project extension; valuation reference from collateral terms |
| 9116 | ValuationCalculationReference / STRING | Yes | Project extension; accepted snapshot calculation reference |
| 9117 | ValuationSecurityISIN / STRING | Yes | Project extension; accepted snapshot ISIN |
| 9118 | ValuationQuantity / QTY | Yes | Project extension; accepted snapshot quantity |
| 9119 | ValuationMarketPrice / PRICE | Yes | Project extension; accepted snapshot market price |
| 9120 | ValuationCurrency / CURRENCY | Yes | Project extension; accepted snapshot currency |
| 9121 | ValuationPriceReference / STRING | Yes | Project extension; accepted snapshot price reference |
| 9122 | ValuationPriceSource / STRING | Yes | Project extension; accepted snapshot price source |
| 9123 | ValuationPriceTimestamp / STRING | Yes | Project extension; exact ISO-8601 price timestamp |
| 9124 | ValuationCalculatedAt / STRING | Yes | Project extension; exact ISO-8601 calculation time |
| 9125 | ValuationLoanMarketValue / AMT | Yes | Project extension; accepted snapshot loan market value |
| 9126 | ValuationRequiredCashCollateral / AMT | Yes | Project extension; accepted snapshot required CASH amount |
| 9127 | ValuationCoveragePercentage / PERCENTAGE | Yes | Project extension; accepted snapshot coverage percent |
| 9128 | ValuationCashHaircutPercentage / PERCENTAGE | Yes | Project extension; accepted snapshot CASH haircut |
| 9129 | CreditReservationAmount / AMT | Yes | Project extension; accepted snapshot credit reservation amount |
| 9130 | ContractLoanMarketValue / AMT | Yes | Project extension; approved contract snapshot market value |
| 9131 | ContractRequiredCashCollateral / AMT | Yes | Project extension; approved contract required CASH collateral |
| 9132 | SettlementCalendarVersion / STRING | Yes | Project extension; existing calendar version |
| 9133 | ContractSettlementDate / LOCALMKTDATE | Yes | Project extension; settlement date |
| 9134 | EventID / STRING | Yes | Project extension; must equal the immutable JSON envelope event ID |
| 9135 | DecisionRespondedAtExact / STRING | Yes | Project extension; exact ISO-8601 decision timestamp, retaining precision beyond standard tag 60 |

Custom AK tags use FIX scalar types and carry one value per field; there is no
JSON or serialized business blob inside FIX. Tag `9134` repeats the immutable
envelope event ID for payload-to-envelope identity validation. Tag `9135`
retains the exact decision timestamp while standard `TransactTime(60)` remains
the millisecond FIX timestamp. The borrower
checks the FIX version/type/dictionary/framing, sender/receiver, event ID,
request correlation, decision version, status enums, and required fields
before translating AK into the existing decision handler's input shape.
Existing ownership/counterparty, request matching, stale-version,
deduplication, and contract-view persistence behavior remain authoritative.
Approval updates the existing borrower request and creates the existing
contract view only; it does not create another Custodian contract or mark
securities settled. Rejection remains unchanged.

The two `FIX44_SL.xml` dictionaries contain the same AK profile and tag
definitions. The AK message profile intentionally defines the required fields
used by this application; it is not the full QuickFIX/J `Confirmation`
message schema.

Example (`|` represents SOH; framing values are computed):

```text
8=FIX.4.4|9=<computed>|35=BB|49=BORROWER-ORG|56=CUSTODIAN-ORG|34=1|52=20261111-06:00:00.123|909=6f4f73de-cb44-47d2-a7f1-ec4a5e381b08|9001=LR-EXAMPLE-001|9002=MSLA-EXAMPLE-001|10=<computed>
```

## FIX 4.4 U2 initial securities settlement confirmation

U2 is a custom application MsgType in both `FIX44_SL.xml` dictionaries; it is
not a standard FIX 4.4 settlement-status message. It is emitted only for the
existing `SECURITY_DELIVERY` instruction after the authorized Custodian
confirmation has committed the settlement, inventory movement, reservation,
credit, and contract-state changes. It does not perform settlement again.
Cash confirmation, pending/failed outcomes, returns, collateral release,
margin, fees, and closure are not U2.

The existing event remains `SETTLEMENT_INSTRUCTION_UPDATED` with its original
deterministic event ID, event type, schema version, correlation ID, aggregate
reference/version, occurred-at value, producer/destination, and parties.
Aggregate version remains `1` for initial delivery. The JSON payload is only
`{"fixMessage":"..."}`; all settlement business fields are encoded as FIX
fields. The outbox enqueue remains in the settlement transaction, and the
Borrower parser reconstructs the existing handler input before its existing
ownership, counterparty, contract-link, version, and inbox-dedup checks.

| Tag | FIX field / type | Required | Contract value, units, and meaning |
|---:|---|:---:|---|
| 35 | MsgType / STRING | Yes | `U2`, project-defined initial securities delivery confirmation |
| 49 / 56 | SenderCompID / TargetCompID | Yes | Custodian / Borrower organization references; must match envelope party fields |
| 34 | MsgSeqNum / SEQNUM | Yes | No-session convention: `aggregateVersion + 1`; initial update is `2` |
| 52 | SendingTime / UTCTIMESTAMP | Yes | Envelope `occurredAt`, UTC to milliseconds |
| 15 | Currency / CURRENCY | Yes | Contract settlement currency, three uppercase letters |
| 22 | IDSource / STRING | Yes | Standard enum `4` (`ISIN`) identifying tag 48 |
| 48 | SecurityID / STRING | Yes | Contract ISIN |
| 60 | TransactTime / UTCTIMESTAMP | Yes | Settlement confirmation time, UTC to milliseconds |
| 9140 | SettlementTransactionReference / STRING | Yes | Project extension; existing contract transaction reference |
| 9141 | SettlementInstructionReference / STRING | Yes | Project extension; instruction reference; equals Kafka correlation and aggregate reference |
| 9142 | SettlementInstructionType / STRING | Yes | Project extension; fixed value `SECURITY_DELIVERY` |
| 9143 | SettlementStatus / STRING | Yes | Project extension; fixed value `SETTLED` |
| 9144 | SettlementContractStatus / STRING | Yes | Project extension; fixed value `ON_LOAN` |
| 9145 | SettlementContractVersion / INT | Yes | Project extension; existing committed contract version |
| 9146 / 9147 | DeliveredQuantity / OutstandingQuantity / QTY | Yes | Project extensions; whole shares; both equal initial quantity at this step |
| 9148 | SettlementConfirmedCashCollateral / AMT | Yes | Project extension; confirmed CASH amount in tag 15 currency |
| 9149 | SettlementExternalConfirmationReference / STRING | Yes | Existing external confirmation reference |
| 9150 | SettlementSimulatedConfirmation / BOOLEAN | Yes | Project extension; `Y` denotes the existing simulated-confirmation path |
| 9151 | SettlementEventID / STRING | Yes | Project extension; must equal unchanged Kafka envelope `eventId` |
| 9152 | SettlementAggregateVersion / INT | Yes | Project extension; fixed value `1` for initial delivery |
| 9153 | SettlementSettledAtExact / STRING | Yes | Project extension; exact ISO-8601 `Instant`, retaining precision beyond tag 60 |
| 9154 | SettlementQuantity / QTY | Yes | Project extension; positive whole-share quantity on the delivery instruction |
| 10 | CheckSum / STRING | Yes | Standard calculated three-digit FIX checksum |

The FIX body length and checksum are verified by the shared FIX 4.4 parsing
path. Borrower additionally checks MsgType, event type, route, envelope/FIX
party match, event ID, aggregate/correlation references, sequence convention,
initial aggregate version, status, quantities, ISIN, currency, and timestamps.
The existing projection compares the contract and request against its
authoritative stored records. Inbox deduplication remains keyed by event ID;
the existing settlement service's business idempotency remains unchanged.
U2 has no rejection status: invalid U2 is rejected by the existing inbox
processing path, and already-queued legacy settlement JSON remains readable.

The custom `U2` message and tags `9140`–`9154` are project extensions in both
dictionaries. Neither the message name nor these fields claim standard FIX
settlement semantics. No new table, socket FIX session, approval change, or
second settlement operation is introduced.

## Example

`|` below represents SOH for readability. `BodyLength` and `CheckSum` are
computed by the builder and are shown as placeholders, not literal wire
values.

```text
8=FIX.4.4|9=<computed>|35=U1|49=BORROWER-ORG|56=CUSTODIAN-ORG|34=1|52=20261111-05:50:00.123|9001=LR-EXAMPLE-001|9002=MSLA-EXAMPLE-001|48=INE000A01012|22=4|38=100|15=INR|64=20261114|9003=https://issuer.example|9004=borrower-user-001|10=<computed>
```

## Sources and pending verification

- [FIX Trading Community FIX Protocol standards](https://www.fixtrading.org/standards/fix-protocol/) — FIX 4.4 standard and tag semantics.
- [QuickFIX/J 3.0.2 FIX 4.4 dictionary](https://github.com/quickfix-j/quickfixj/blob/QFJ_RELEASE_3_0_2/quickfixj-messages/quickfixj-messages-fix44/src/main/resources/FIX44.xml) — BB standard message and field definitions.
- [QuickFIX/J 3.0.2 FIX 4.4 dictionary](https://github.com/quickfix-j/quickfixj/blob/QFJ_RELEASE_3_0_2/quickfixj-messages/quickfixj-messages-fix44/src/main/resources/FIX44.xml) — BA standard message and field definitions.
- [QuickFIX/J 3.0.2 FIX 4.4 dictionary](https://github.com/quickfix-j/quickfixj/blob/QFJ_RELEASE_3_0_2/quickfixj-messages/quickfixj-messages-fix44/src/main/resources/FIX44.xml) — AK standard `Confirmation` and standard field meanings.
- [QuickFIX/J 3.0.2 source](https://github.com/quickfix-j/quickfixj/tree/QFJ_RELEASE_3_0_2) — parser/runtime version used by both modules.
- The two checked-in `FIX44_SL.xml` files are the executable custom dictionaries
  for this project; they are not the complete unmodified QuickFIX/J FIX 4.4
  dictionary.

Borrower/Custodian test-source compilation passed. The focused approval tests
passed (Borrower: 5, including AK decoding and rejection handling; Custodian:
2, including AK generation). The custom dictionaries are valid, byte-identical
XML. Live Kafka verification for U1, BB, BA, and AK is pending local
service/broker readiness and authenticated integration-test prerequisites.
Replay and concurrency scenarios remain pending.
