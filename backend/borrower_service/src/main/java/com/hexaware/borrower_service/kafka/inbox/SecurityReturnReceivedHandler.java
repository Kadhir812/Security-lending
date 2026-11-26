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
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SecurityReturnReceivedHandler implements MessageHandler {
  private final LoanContractViewRepository contracts;
  private final LoanRequestRepository requests;
  private final Clock clock;

  @Override
  public String eventType() {
    return "SECURITY_RETURN_RECEIVED";
  }

  @Override
  @Transactional
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String instructionReference = text(payload.get("instructionReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long version = number(event.getAggregateVersion());
    long quantity = number(payload.get("quantity"));
    if (!"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || number(payload.get("payloadVersion")) != 1
        || number(payload.get("aggregateVersion")) != version
        || !Objects.equals(event.getAggregateReference(), instructionReference)
        || !Objects.equals(event.getCorrelationId(), instructionReference)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || !Objects.equals(payload.get("instructionStatus"), "PENDING")
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || version != 1
        || transactionReference == null || instructionReference == null
        || borrowerReference == null || custodianReference == null || quantity <= 0) {
      throw invalid("SECURITY_RETURN_RECEIPT_ENVELOPE_MISMATCH");
    }

    LoanContractView view = contracts.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request = requests.findByRequestReference(view.getRequestReference())
        .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())
        || !Objects.equals(request.getSecurityIsin(), payload.get("securityIsin"))
        || !Objects.equals(request.getSettlementCurrency(), payload.get("currency"))) {
      throw invalid("SECURITY_RETURN_RECEIPT_TERMS_MISMATCH");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> returns = mapList(snapshot.get("returnInstructions"));
    Map<String, Object> instruction = find(returns, instructionReference);
    if (instruction == null) throw invalid("RETURN_RECEIPT_WITHOUT_BORROWER_INSTRUCTION");
    if (number(instruction.get("quantity")) != quantity
        || !Objects.equals(instruction.get("recallReference"), payload.get("recallReference"))
        || !Objects.equals(instruction.get("securityIsin"), payload.get("securityIsin"))
        || !Objects.equals(instruction.get("currency"), payload.get("currency"))) {
      throw invalid("RETURN_RECEIPT_DOES_NOT_MATCH_SUBMISSION");
    }
    long currentVersion = number(instruction.get("settlementAggregateVersion"));
    if (version < currentVersion) return;
    if (version == currentVersion && currentVersion > 0) {
      if ("PENDING".equals(instruction.get("status"))) return;
      throw invalid("CONFLICTING_RETURN_RECEIPT_VERSION");
    }
    if (version != currentVersion + 1 || !"SUBMITTED".equals(instruction.get("status"))) {
      throw invalid("RETURN_RECEIPT_VERSION_GAP");
    }
    instruction.put("status", "PENDING");
    instruction.put("deliveryStatus", "DELIVERED");
    instruction.put("custodianReceivedAt", event.getOccurredAt().toString());
    instruction.put("settlementAggregateVersion", version);
    snapshot.put("returnInstructions", returns);
    view.setContractSnapshot(snapshot);
    contracts.save(view);
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String reference) {
    return rows.stream()
        .filter(row -> reference.equals(row.get("instructionReference")))
        .findFirst()
        .orElse(null);
  }

  private List<Map<String, Object>> mapList(Object value) {
    List<Map<String, Object>> rows = new ArrayList<>();
    if (value instanceof List<?> values) {
      for (Object item : values) {
        if (item instanceof Map<?, ?> source) {
          Map<String, Object> row = new LinkedHashMap<>();
          source.forEach((key, field) -> {
            if (key instanceof String name) row.put(name, field);
          });
          rows.add(row);
        }
      }
    }
    return rows;
  }

  private long number(Object value) {
    if (value == null) return 0;
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_RETURN_RECEIPT_NUMBER");
    }
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
