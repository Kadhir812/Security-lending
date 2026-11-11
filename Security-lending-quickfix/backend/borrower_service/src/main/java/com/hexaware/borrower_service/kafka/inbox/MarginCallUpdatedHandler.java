package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarginCallUpdatedHandler implements MessageHandler {
  private final LoanContractViewRepository contracts;
  private final LoanRequestRepository requests;
  private final Clock clock;

  @Override
  public String eventType() {
    return "MARGIN_CALL_UPDATED";
  }

  @Override
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String callReference = text(payload.get("marginCallReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    if (!"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || integer(payload.get("payloadVersion")) != 1
        || !Objects.equals(event.getAggregateReference(), callReference)
        || !Objects.equals(event.getCorrelationId(), callReference)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || !Objects.equals(event.getAggregateVersion(), integer(payload.get("aggregateVersion")))
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || transactionReference == null
        || callReference == null
        || borrowerReference == null
        || custodianReference == null
        || !List.of("OPEN", "PARTIALLY_SATISFIED", "SATISFIED", "FAILED", "BREACHED", "CANCELLED")
            .contains(payload.get("status"))) {
      throw invalid("MARGIN_CALL_EVENT_ENVELOPE_MISMATCH");
    }

    LoanContractView view = contracts.findByTransactionReference(transactionReference)
        .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request = requests.findByRequestReference(view.getRequestReference())
        .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())) {
      throw invalid("MARGIN_CALL_EVENT_PARTIES_MISMATCH");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    Map<String, Object> versions = map(snapshot.get("marginCallEventVersions"));
    long incomingVersion = event.getAggregateVersion();
    Object priorValue = versions.get(callReference);
    if (priorValue != null) {
      long priorVersion = integer(priorValue);
      if (incomingVersion < priorVersion) return;
      if (incomingVersion == priorVersion) {
        Map<String, Object> priorCall = findCall(snapshot, callReference);
        if (priorCall != null
            && Objects.equals(priorCall.get("status"), payload.get("status"))
            && Objects.equals(priorCall.get("outstandingAmount"), payload.get("outstandingAmount"))
            && Objects.equals(priorCall.get("valuationResultReference"),
                payload.get("valuationResultReference"))
            && Objects.equals(priorCall.get("instructionReference"),
                payload.get("instructionReference"))
            && Objects.equals(priorCall.get("settlementStatus"),
                payload.get("settlementStatus"))) {
          return;
        }
        throw invalid("CONFLICTING_MARGIN_CALL_VERSION");
      }
    }

    List<Map<String, Object>> marginCalls = list(snapshot.get("marginCalls"));
    Map<String, Object> call = new LinkedHashMap<>();
    call.put("marginCallReference", callReference);
    call.put("transactionReference", transactionReference);
    call.put("status", text(payload.get("status")));
    call.put("requiredCollateral", text(payload.get("requiredCollateral")));
    call.put("currentCollateral", text(payload.get("currentCollateral")));
    call.put("outstandingAmount", text(payload.get("outstandingAmount")));
    call.put("dueAt", text(payload.get("dueAt")));
    call.put("valuationResultReference", text(payload.get("valuationResultReference")));
    call.put("updatedAt", text(payload.get("updatedAt")));
    call.put("aggregateVersion", incomingVersion);
    call.put("instructionReference", payload.get("instructionReference"));
    call.put("settlementStatus", payload.get("settlementStatus"));
    marginCalls.removeIf(existing ->
        callReference.equals(existing.get("marginCallReference")));
    marginCalls.add(call);
    versions.put(callReference, incomingVersion);
    snapshot.put("marginCalls", marginCalls);
    snapshot.put("marginCallEventVersions", versions);
    snapshot.put("contractStatus", "ON_LOAN");
    updateTopUp(snapshot, payload);
    view.setContractSnapshot(snapshot);
    contracts.save(view);
  }

  private void updateTopUp(Map<String, Object> snapshot, Map<String, Object> payload) {
    String instructionReference = text(payload.get("instructionReference"));
    if (instructionReference == null) return;
    List<Map<String, Object>> instructions = list(snapshot.get("collateralInstructions"));
    for (Map<String, Object> instruction : instructions) {
      if (instructionReference.equals(instruction.get("instructionReference"))) {
        instruction.put("status", "POSTED");
        instruction.put("settlementStatus", payload.get("settlementStatus"));
        instruction.put("settlementAmount", payload.get("settlementAmount"));
        instruction.put("settlementCurrency", payload.get("settlementCurrency"));
        instruction.put("simulatedConfirmation", payload.get("simulatedConfirmation"));
        instruction.put("externalConfirmationReference",
            payload.get("externalConfirmationReference"));
      }
    }
    snapshot.put("collateralInstructions", instructions);
  }

  private Map<String, Object> findCall(Map<String, Object> snapshot, String reference) {
    for (Map<String, Object> call : list(snapshot.get("marginCalls"))) {
      if (reference.equals(call.get("marginCallReference"))) return call;
    }
    return null;
  }

  private List<Map<String, Object>> list(Object raw) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (raw instanceof List<?> values) {
      for (Object value : values) {
        if (value instanceof Map<?, ?> source) result.add(map(source));
      }
    }
    return result;
  }

  private Map<String, Object> map(Object raw) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (raw instanceof Map<?, ?> source) {
      source.forEach((key, value) -> {
        if (key instanceof String field) result.put(field, value);
      });
    }
    return result;
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private long integer(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_MARGIN_CALL_VERSION");
    }
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
