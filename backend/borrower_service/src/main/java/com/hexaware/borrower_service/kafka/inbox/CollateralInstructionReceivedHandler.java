package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import java.math.BigDecimal;
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
public class CollateralInstructionReceivedHandler implements MessageHandler {
  private final LoanContractViewRepository contracts;
  private final LoanRequestRepository requests;
  private final Clock clock;

  @Override
  public String eventType() {
    return "COLLATERAL_INSTRUCTION_RECEIVED";
  }

  @Override
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String instructionReference = text(payload.get("instructionReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long incomingVersion = integer(event.getAggregateVersion());
    if (!"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || integer(payload.get("payloadVersion")) != 1
        || !Objects.equals(event.getAggregateReference(), instructionReference)
        || !Objects.equals(event.getCorrelationId(), instructionReference)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || event.getCausationEventId() == null
        || incomingVersion < 1
        || !Objects.equals(integer(payload.get("aggregateVersion")), incomingVersion)
        || !Objects.equals(payload.get("instructionStatus"), "PENDING")
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || transactionReference == null
        || instructionReference == null
        || borrowerReference == null
        || custodianReference == null) {
      throw invalid("COLLATERAL_RECEIPT_ENVELOPE_MISMATCH");
    }

    LoanContractView view = contracts.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request = requests.findByRequestReference(view.getRequestReference())
        .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())) {
      throw invalid("COLLATERAL_RECEIPT_PARTIES_MISMATCH");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> instructions = mapList(snapshot.get("collateralInstructions"));
    for (Map<String, Object> instruction : instructions) {
      if (!instructionReference.equals(instruction.get("instructionReference"))) continue;
      if (!Objects.equals(instruction.get("currency"), payload.get("currency"))
          || decimal(instruction.get("amount")).compareTo(decimal(payload.get("amount"))) != 0
          || !Objects.equals(instruction.get("marginCallReference"),
              payload.get("marginCallReference"))) {
        throw invalid("COLLATERAL_RECEIPT_DOES_NOT_MATCH_SUBMISSION");
      }
      long priorVersion = instruction.get("settlementAggregateVersion") == null
          ? 0
          : integer(instruction.get("settlementAggregateVersion"));
      if (incomingVersion < priorVersion) return;
      if (incomingVersion == priorVersion && priorVersion > 0) {
        if ("PENDING".equals(instruction.get("status"))) return;
        throw invalid("CONFLICTING_COLLATERAL_RECEIPT_VERSION");
      }
      if (incomingVersion != priorVersion + 1) {
        throw invalid("COLLATERAL_RECEIPT_VERSION_GAP");
      }
      if (!"POSTED".equals(instruction.get("status"))) {
        instruction.put("status", "PENDING");
        instruction.put("deliveryStatus", "DELIVERED");
        instruction.put("custodianReceivedAt", event.getOccurredAt().toString());
        instruction.put("settlementAggregateVersion", incomingVersion);
      }
      snapshot.put("collateralInstructions", instructions);
      view.setContractSnapshot(snapshot);
      contracts.save(view);
      return;
    }
    throw invalid("COLLATERAL_RECEIPT_WITHOUT_BORROWER_INSTRUCTION");
  }

  private List<Map<String, Object>> mapList(Object value) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (value instanceof List<?> values) {
      for (Object item : values) {
        if (item instanceof Map<?, ?> source) {
          Map<String, Object> copy = new LinkedHashMap<>();
          source.forEach((key, field) -> {
            if (key instanceof String name) copy.put(name, field);
          });
          result.add(copy);
        }
      }
    }
    return result;
  }

  private BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_COLLATERAL_AMOUNT");
    }
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
      throw invalid("INVALID_COLLATERAL_RECEIPT_VERSION");
    }
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
