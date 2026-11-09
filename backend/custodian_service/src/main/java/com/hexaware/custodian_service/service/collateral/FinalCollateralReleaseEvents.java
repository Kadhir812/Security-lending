package com.hexaware.custodian_service.service.collateral;

import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinalCollateralReleaseEvents {
  private static final String SETTLEMENT_EVENT = "SETTLEMENT_INSTRUCTION_UPDATED";
  private static final String CLOSURE_EVENT = "CONTRACT_CLOSED";

  private final OutgoingEventService outgoing;
  private final Clock clock;

  public void publishReleaseUpdate(
      LoanContract contract,
      SettlementInstruction instruction,
      FinalReleaseParties parties,
      String releaseReference,
      int releaseInstructionCount,
      String status,
      long aggregateVersion,
      boolean simulatedConfirmation) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", contract.getTransactionReference());
    payload.put("instructionReference", instruction.getInstructionReference());
    payload.put("instructionType", "COLLATERAL_RELEASE");
    payload.put("releaseReference", releaseReference);
    payload.put("releaseInstructionCount", releaseInstructionCount);
    payload.put("aggregateVersion", aggregateVersion);
    payload.put("borrowerOrganizationReference", parties.borrowerReference());
    payload.put("custodianOrganizationReference", parties.custodianReference());
    payload.put("settlementStatus", status);
    payload.put("instructionStatus", status);
    payload.put("amount", instruction.getAmount().toPlainString());
    payload.put("currency", instruction.getCurrency());
    payload.put("contractStatus", contract.getContractStatus().name());
    payload.put("contractVersion", contract.getVersion());
    payload.put(
        "settledAt",
        instruction.getSettledAt() == null ? null : instruction.getSettledAt().toString());
    payload.put("externalConfirmationReference", instruction.getExternalConfirmationReference());
    payload.put("failureReason", instruction.getFailureReason());
    payload.put("simulatedConfirmation", simulatedConfirmation);
    outgoing.enqueue(
        "SETTLEMENT_INSTRUCTION",
        EventEnvelope.builder()
            .eventId(
                UUID.nameUUIDFromBytes(
                        (SETTLEMENT_EVENT
                                + ":"
                                + instruction.getInstructionReference()
                                + ":"
                                + aggregateVersion)
                            .getBytes(StandardCharsets.UTF_8))
                    .toString())
            .eventType(SETTLEMENT_EVENT)
            .schemaVersion(1)
            .producerService("CUSTODIAN")
            .destinationService("BORROWER")
            .correlationId(instruction.getInstructionReference())
            .aggregateReference(instruction.getInstructionReference())
            .aggregateVersion(aggregateVersion)
            .occurredAt(clock.instant())
            .senderOrganizationReference(parties.custodianReference())
            .receiverOrganizationReference(parties.borrowerReference())
            .payload(payload)
            .build());
  }

  public void publishClosed(
      LoanContract contract,
      FinalReleaseParties parties,
      String closureReference,
      String reason,
      String eventId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", contract.getTransactionReference());
    payload.put("closureReference", closureReference);
    payload.put("reason", reason);
    payload.put("contractStatus", "CLOSED");
    payload.put("contractVersion", contract.getVersion());
    payload.put("deliveredQuantity", contract.getDeliveredQuantity());
    payload.put("returnedQuantity", contract.getReturnedQuantity());
    payload.put("outstandingQuantity", 0);
    payload.put("confirmedCashCollateral", "0.00");
    payload.put("borrowerOrganizationReference", parties.borrowerReference());
    payload.put("custodianOrganizationReference", parties.custodianReference());
    payload.put("closedAt", clock.instant().toString());
    outgoing.enqueue(
        "CONTRACT_CLOSED",
        EventEnvelope.builder()
            .eventId(eventId)
            .eventType(CLOSURE_EVENT)
            .schemaVersion(1)
            .producerService("CUSTODIAN")
            .destinationService("BORROWER")
            .correlationId(contract.getTransactionReference())
            .aggregateReference(contract.getTransactionReference())
            .aggregateVersion(contract.getVersion())
            .occurredAt(clock.instant())
            .senderOrganizationReference(parties.custodianReference())
            .receiverOrganizationReference(parties.borrowerReference())
            .payload(payload)
            .build());
  }
}
