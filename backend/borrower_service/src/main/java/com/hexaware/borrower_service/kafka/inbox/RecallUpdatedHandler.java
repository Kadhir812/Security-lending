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
public class RecallUpdatedHandler implements MessageHandler {
  private final LoanContractViewRepository contracts;
  private final LoanRequestRepository requests;
  private final Clock clock;

  @Override
  public String eventType() {
    return "RECALL_UPDATED";
  }

  @Override
  @Transactional
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String recallReference = text(payload.get("recallReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long version = number(event.getAggregateVersion());
    long requested = number(payload.get("requestedQuantity"));
    long returned = number(payload.get("returnedQuantity"));
    if (!"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || number(payload.get("payloadVersion")) != 1
        || number(payload.get("aggregateVersion")) != version
        || !Objects.equals(event.getAggregateReference(), recallReference)
        || !Objects.equals(event.getCorrelationId(), recallReference)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || transactionReference == null || recallReference == null
        || borrowerReference == null || custodianReference == null
        || requested <= 0 || returned < 0 || returned > requested
        || text(payload.get("securityIsin")) == null
        || text(payload.get("dueAt")) == null
        || text(payload.get("reason")) == null
        || !List.of("OPEN", "PROCESSING", "PARTIALLY_RETURNED", "COMPLETED",
            "OVERDUE", "DEFAULTED", "CANCELLED").contains(text(payload.get("status")))) {
      throw invalid("RECALL_EVENT_ENVELOPE_MISMATCH");
    }

    LoanContractView view = contracts.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request = requests.findByRequestReference(view.getRequestReference())
        .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())
        || !Objects.equals(request.getSecurityIsin(), payload.get("securityIsin"))) {
      throw invalid("RECALL_PARTIES_OR_SECURITY_MISMATCH");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> recalls = mapList(snapshot.get("recalls"));
    Map<String, Object> existing = recalls.stream()
        .filter(row -> recallReference.equals(row.get("recallReference")))
        .findFirst()
        .orElse(null);
    if (existing != null) {
      long currentVersion = number(existing.get("aggregateVersion"));
      if (version < currentVersion) return;
      if (version == currentVersion) {
        if (sameRecall(existing, payload)) return;
        throw invalid("CONFLICTING_RECALL_VERSION");
      }
      if (version != currentVersion + 1) throw invalid("RECALL_VERSION_GAP");
      existing.putAll(payload);
      existing.put("aggregateVersion", version);
    } else {
      if (version != 0) throw invalid("RECALL_VERSION_GAP");
      Map<String, Object> recall = new LinkedHashMap<>(payload);
      recall.put("aggregateVersion", version);
      recalls.add(recall);
    }
    snapshot.put("recalls", recalls);
    view.setContractSnapshot(snapshot);
    contracts.save(view);
  }

  private boolean sameRecall(Map<String, Object> left, Map<String, Object> right) {
    return Objects.equals(left.get("status"), right.get("status"))
        && number(left.get("requestedQuantity")) == number(right.get("requestedQuantity"))
        && number(left.get("returnedQuantity")) == number(right.get("returnedQuantity"))
        && Objects.equals(left.get("dueAt"), right.get("dueAt"));
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
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_RECALL_NUMBER");
    }
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
