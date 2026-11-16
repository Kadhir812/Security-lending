package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.entity.LocateRequest;
import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.enums.SubmissionStatus;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocateRequestDecisionHandler implements MessageHandler {
  private final LocateRequestRepository locates;
  private final Clock clock;

  @Override
  public String eventType() {
    return "LOCATE_REQUEST_DECISION";
  }

  @Override
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String reference = text(payload.get("locateReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long version = number(payload.get("decisionVersion"));
    if (number(payload.get("payloadVersion")) != 1) {
      throw invalid("UNSUPPORTED_LOCATE_DECISION_VERSION");
    }
    LocateRequest locate =
        locates.findLockedByLocateReference(reference)
            .orElseThrow(() -> invalid("UNKNOWN_LOCATE_REQUEST"));
    Instant respondedAt = instant(payload.get("respondedAt"));
    Instant expiryTime = instant(payload.get("expiryTime"));
    String reason = optionalText(payload.get("responseReason"));
    String statusText = text(payload.get("decisionStatus"));

    if (!"LOCATE_REQUEST_DECISION".equals(event.getEventType())
        || !"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || !Objects.equals(event.getCorrelationId(), reference)
        || !Objects.equals(event.getAggregateReference(), reference)
        || !Objects.equals(event.getAggregateVersion(), version)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || respondedAt == null
        || respondedAt.isAfter(clock.instant())
        || respondedAt.isAfter(event.getOccurredAt())
        || !Objects.equals(locate.getBorrowerOrganizationReference(), borrowerReference)
        || !Objects.equals(locate.getCustodianOrganizationReference(), custodianReference)
        || !Objects.equals(locate.getMslaReference(), text(payload.get("mslaReference")))
        || !Objects.equals(locate.getSecurityIsin(), text(payload.get("securityIsin")))
        || number(payload.get("quantity")) != locate.getQuantity()) {
      throw invalid("LOCATE_DECISION_PARTIES_OR_CORRELATION_MISMATCH");
    }

    LocateStatus decision;
    try {
      decision = LocateStatus.valueOf(statusText);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw invalid("INVALID_LOCATE_DECISION_STATUS");
    }
    Long approvedQuantity = optionalNumber(payload.get("approvedQuantity"));
    if (decision == LocateStatus.APPROVED) {
      if (reason != null
          || approvedQuantity == null
          || approvedQuantity <= 0
          || approvedQuantity > locate.getQuantity()
          || (expiryTime != null && !expiryTime.isAfter(respondedAt))) {
        throw invalid("INVALID_LOCATE_APPROVAL");
      }
    } else if (decision != LocateStatus.REJECTED
        || reason == null
        || reason.length() > 500
        || approvedQuantity != null) {
      throw invalid("INVALID_LOCATE_REJECTION");
    }

    Long currentVersion = locate.getDecisionSourceVersion();
    if (currentVersion != null && version < currentVersion) return;
    if (currentVersion != null && version == currentVersion) {
      if (locate.getStatus() == decision
          && Objects.equals(locate.getApprovedQuantity(), approvedQuantity)
          && Objects.equals(locate.getResponseReason(), reason)) {
        return;
      }
      throw invalid("CONFLICTING_LOCATE_DECISION_VERSION");
    }
    if (version != 1 || locate.getStatus() != LocateStatus.PENDING) {
      throw invalid("CONFLICTING_TERMINAL_LOCATE_DECISION");
    }

    locate.setStatus(decision);
    locate.setApprovedQuantity(approvedQuantity);
    locate.setResponseReason(reason);
    locate.setRespondedAt(respondedAt);
    if (expiryTime != null) locate.setExpiryTime(expiryTime);
    locate.setDecisionSourceVersion(version);
    locate.setSubmissionStatus(SubmissionStatus.SENT.name());
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private String optionalText(Object value) {
    return value == null ? null : text(value);
  }

  private long number(Object value) {
    if (value instanceof Number number) return number.longValue();
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_LOCATE_DECISION_NUMBER");
    }
  }

  private Long optionalNumber(Object value) {
    return value == null ? null : number(value);
  }

  private Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof String text) {
      try {
        return Instant.parse(text);
      } catch (java.time.format.DateTimeParseException exception) {
        throw invalid("INVALID_LOCATE_DECISION_TIMESTAMP");
      }
    }
    return null;
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
