package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ContractClosedHandler implements MessageHandler {
  private final LoanContractViewRepository contracts;
  private final LoanRequestRepository requests;
  private final Clock clock;

  @Override
  public String eventType() {
    return "CONTRACT_CLOSED";
  }

  @Override
  @Transactional
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String closureReference = text(payload.get("closureReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long incomingVersion = integer(event.getAggregateVersion());
    String expectedEventId = eventId(transactionReference, closureReference);
    if (!"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || integer(payload.get("payloadVersion")) != 1
        || !Objects.equals(event.getEventId(), expectedEventId)
        || !Objects.equals(event.getAggregateReference(), transactionReference)
        || !Objects.equals(event.getCorrelationId(), transactionReference)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || integer(payload.get("contractVersion")) != incomingVersion
        || !"CLOSED".equals(payload.get("contractStatus"))
        || text(payload.get("reason")) == null
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || !validReference(transactionReference)
        || !validReference(closureReference)
        || !validReference(borrowerReference)
        || !validReference(custodianReference)) {
      throw invalid("CONTRACT_CLOSED_ENVELOPE_MISMATCH");
    }
    Instant closedAt;
    try {
      closedAt = Instant.parse(text(payload.get("closedAt")));
    } catch (RuntimeException exception) {
      throw invalid("CONTRACT_CLOSED_TIMESTAMP_INVALID");
    }
    if (closedAt.isAfter(clock.instant())) throw invalid("CONTRACT_CLOSED_TIMESTAMP_INVALID");
    long deliveredQuantity = integer(payload.get("deliveredQuantity"));
    long returnedQuantity = integer(payload.get("returnedQuantity"));
    long outstandingQuantity = integer(payload.get("outstandingQuantity"));
    if (deliveredQuantity <= 0
        || returnedQuantity != deliveredQuantity
        || outstandingQuantity != 0
        || decimal(payload.get("confirmedCashCollateral")).signum() != 0) {
      throw invalid("CONTRACT_CLOSED_BALANCES_MISMATCH");
    }

    LoanContractView view = contracts.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request = requests.findByRequestReference(view.getRequestReference())
        .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())) {
      throw invalid("CONTRACT_CLOSED_PARTIES_MISMATCH");
    }
    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    long currentVersion = number(snapshot.get("settlementContractVersion"));
    if (incomingVersion < currentVersion) return;
    if (incomingVersion == currentVersion && currentVersion > 0) {
      if ("CLOSED".equals(snapshot.get("contractStatus"))
          && Objects.equals(snapshot.get("closureEventId"), event.getEventId())) {
        return;
      }
      throw invalid("CONFLICTING_CONTRACT_CLOSURE_VERSION");
    }
    if (incomingVersion <= currentVersion) {
      throw invalid("CONTRACT_CLOSURE_VERSION_INVALID");
    }
    snapshot.put("contractStatus", "CLOSED");
    snapshot.put("settlementContractVersion", incomingVersion);
    snapshot.put("deliveredQuantity", deliveredQuantity);
    snapshot.put("returnedQuantity", returnedQuantity);
    snapshot.put("outstandingQuantity", outstandingQuantity);
    snapshot.put("confirmedCashCollateral", payload.get("confirmedCashCollateral"));
    snapshot.put("closureReference", closureReference);
    snapshot.put("closureEventId", event.getEventId());
    snapshot.put("closedAt", closedAt.toString());
    Object raw = snapshot.get("collateralReleaseRequests");
    if (raw instanceof List<?> rows) {
      for (Object row : rows) {
        if (row instanceof Map<?, ?> map
            && !"SETTLED".equals(map.get("status"))
            && !"COMPLETED".equals(map.get("status"))) {
          @SuppressWarnings("unchecked")
          Map<String, Object> release = (Map<String, Object>) map;
          release.put("status", "COMPLETED");
          release.put("updatedAt", closedAt.toString());
        }
      }
    }
    view.setContractSnapshot(snapshot);
    contracts.save(view);
  }

  private String eventId(String transactionReference, String closureReference) {
    if (transactionReference == null || closureReference == null) return null;
    return UUID.nameUUIDFromBytes(
        ("CONTRACT_CLOSED:" + transactionReference + ":" + closureReference)
            .getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private boolean validReference(String value) {
    return value != null && value.matches("[A-Za-z0-9._:-]{1,80}");
  }

  private long number(Object value) {
    return value == null ? 0 : integer(value);
  }

  private long integer(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (RuntimeException exception) {
      throw invalid("INVALID_CONTRACT_CLOSURE_VERSION");
    }
  }

  private java.math.BigDecimal decimal(Object value) {
    try {
      return new java.math.BigDecimal(String.valueOf(value));
    } catch (RuntimeException exception) {
      throw invalid("CONTRACT_CLOSED_BALANCES_MISMATCH");
    }
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
