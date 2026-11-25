package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.enums.LoanRequestStatus;
import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanRequestDecisionHandler implements MessageHandler {
  private final LoanRequestRepository requests;
  private final LocateRequestRepository locates;
  private final LoanContractViewRepository contracts;
  private final LoanRequestFixMessageBuilder fixMessages;
  private final Clock clock;

  @Override
  public String eventType() {
    return "LOAN_REQUEST_DECISION";
  }

  @Override
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    Object rawFixMessage = payload.get("fixMessage");
    if (payload.containsKey("fixMessage")) {
      if (!(rawFixMessage instanceof String fixMessage) || payload.size() != 1) {
        throw invalid("INVALID_FIX_AK_OUTBOX_PAYLOAD");
      }
      try {
        payload = fixMessages.parseApprovalConfirmationAndValidate(event, fixMessage);
      } catch (IllegalArgumentException exception) {
        throw invalid("INVALID_FIX_AK_APPROVAL_CONFIRMATION");
      }
    }
    String requestReference = text(payload.get("requestReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long payloadVersion = integer(payload.get("payloadVersion"));
    String mslaReference = optionalText(payload.get("mslaReference"));
    LoanRequest request =
        requests
            .findByRequestReference(requestReference)
            .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    long decisionVersion = integer(payload.get("decisionVersion"));
    String decisionName = text(payload.get("decisionStatus"));
    String reason = optionalText(payload.get("decisionReason"));
    Instant respondedAt = instant(payload.get("respondedAt"));
    Object rawContract = payload.get("contract");

    if (!"LOAN_REQUEST_DECISION".equals(event.getEventType())
        || !"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || !Objects.equals(event.getCorrelationId(), requestReference)
        || !Objects.equals(event.getAggregateReference(), requestReference)
        || !Objects.equals(event.getAggregateVersion(), decisionVersion)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || event.getSchemaVersion() != 1
        || payloadVersion != 1
        || !Objects.equals(request.getBorrowerOrganizationReference(), borrowerReference)
        || !Objects.equals(request.getCustodianOrganizationReference(), custodianReference)
        || (mslaReference != null && !Objects.equals(request.getMslaReference(), mslaReference))
        || decisionVersion < 1
        || respondedAt == null
        || respondedAt.isAfter(clock.instant())
        || respondedAt.isAfter(event.getOccurredAt())) {
      throw invalid("DECISION_EVENT_PARTIES_OR_CORRELATION_MISMATCH");
    }

    LoanRequestStatus decision;
    try {
      decision = LoanRequestStatus.valueOf(decisionName);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw invalid("INVALID_DECISION_STATUS");
    }
    if (decision != LoanRequestStatus.APPROVED && decision != LoanRequestStatus.REJECTED) {
      throw invalid("NON_TERMINAL_DECISION_STATUS");
    }

    Map<String, Object> contractSnapshot = null;
    String transactionReference = null;
    if (decision == LoanRequestStatus.APPROVED) {
      if (reason != null) throw invalid("APPROVAL_MUST_NOT_HAVE_REJECTION_REASON");
      if (!(rawContract instanceof Map<?, ?> contract)) {
        throw invalid("APPROVED_DECISION_MISSING_CONTRACT");
      }
      Object rawReference = contract.get("transactionReference");
      if (!(rawReference instanceof String reference) || reference.isBlank()) {
        throw invalid("INVALID_CONTRACT_REFERENCE");
      }
      if (!Objects.equals(contract.get("requestReference"), requestReference)
          || !Objects.equals(contract.get("securityIsin"), request.getSecurityIsin())
          || integer(contract.get("quantity")) != request.getQuantity()
          || !Objects.equals(contract.get("settlementCurrency"), request.getSettlementCurrency())
          || !Objects.equals(
              contract.get("settlementDate"),
              request.getRequestedSettlementDate().toString())
          || !Objects.equals(
              contract.get("settlementCurrency"), request.getSettlementCurrency())
          || !validCashTerms(contract)) {
        throw invalid("CONTRACT_REQUEST_MISMATCH");
      }
      transactionReference = reference;
      Map<String, Object> checkedSnapshot = new java.util.LinkedHashMap<>();
      contract.forEach(
          (key, value) -> {
            if (!(key instanceof String field)) throw invalid("INVALID_CONTRACT_FIELD");
            checkedSnapshot.put(field, value);
          });
      checkedSnapshot.put("contractStatus", "APPROVED");
      checkedSnapshot.put("borrowerOrganizationReference", borrowerReference);
      checkedSnapshot.put("custodianOrganizationReference", custodianReference);
      checkedSnapshot.putIfAbsent("deliveredQuantity", 0L);
      checkedSnapshot.putIfAbsent("returnedQuantity", 0L);
      checkedSnapshot.putIfAbsent("outstandingQuantity", 0L);
      contractSnapshot = checkedSnapshot;
    } else if (rawContract != null || reason == null || reason.isBlank()) {
      throw invalid("INVALID_REJECTION_DECISION");
    }

    Long currentSourceVersion = request.getDecisionSourceVersion();
    if (currentSourceVersion != null && decisionVersion < currentSourceVersion) return;
    if (currentSourceVersion != null && decisionVersion == currentSourceVersion) {
      if (request.getStatus() == decision
          && Objects.equals(request.getResponseReason(), reason)
          && existingContractMatches(requestReference, transactionReference)) {
        return;
      }
      throw invalid("CONFLICTING_DECISION_VERSION");
    }
    if (request.getStatus() != LoanRequestStatus.PENDING) {
      throw invalid("CONFLICTING_TERMINAL_DECISION");
    }

    request.setStatus(decision);
    request.setResponseReason(reason);
    request.setRespondedAt(respondedAt);
    request.setDecisionSourceVersion(decisionVersion);
    if (decision == LoanRequestStatus.APPROVED) {
      markLocateUsed(request);
      contracts.save(
          LoanContractView.builder()
              .requestReference(requestReference)
              .transactionReference(transactionReference)
              .contractSnapshot(contractSnapshot)
              .receivedAt(clock.instant())
              .build());
    }

  }

  private void markLocateUsed(LoanRequest request) {
    if (request.getLocateId() == null) return;
    var locate =
        locates
            .findLockedByLocateId(request.getLocateId())
            .orElseThrow(() -> invalid("APPROVED_LOAN_LOCATE_NOT_FOUND"));
    if (locate.getStatus() != LocateStatus.APPROVED
        || locate.getApprovedQuantity() == null
        || locate.getApprovedQuantity() < request.getQuantity()) {
      throw invalid("APPROVED_LOAN_LOCATE_NOT_AVAILABLE");
    }
    locate.setStatus(LocateStatus.USED);
  }

  private boolean existingContractMatches(String requestReference, String transactionReference) {
    if (transactionReference == null) {
      return contracts.findByRequestReference(requestReference).isEmpty();
    }
    return contracts
        .findByRequestReference(requestReference)
        .map(view -> transactionReference.equals(view.getTransactionReference()))
        .orElse(false);
  }

  private boolean validCashTerms(Map<?, ?> contract) {
    Object rawTerms = contract.get("collateralTerms");
    Object rawSnapshot = contract.get("acceptedValuationSnapshot");
    if (!(rawTerms instanceof Map<?, ?> terms)
        || !(rawSnapshot instanceof Map<?, ?> snapshot)
        || !"CASH".equals(terms.get("collateralType"))
        || !Objects.equals(terms.get("currency"), contract.get("settlementCurrency"))
        || decimal(terms.get("haircutPercentage")).signum() != 0
        || decimal(terms.get("coveragePercentage"))
            .compareTo(decimal(contract.get("collateralCoveragePct"))) != 0
        || decimal(contract.get("loanMarketValue")).signum() <= 0
        || decimal(contract.get("requiredCashCollateral")).signum() <= 0
        || text(snapshot.get("priceReference")) == null
        || text(snapshot.get("priceSource")) == null
        || !Objects.equals(snapshot.get("currency"), contract.get("settlementCurrency"))) {
      return false;
    }
    return true;
  }

  private BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_CONTRACT_AMOUNT");
    }
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private String optionalText(Object value) {
    if (value == null) return null;
    if (value instanceof String text) return text;
    throw invalid("INVALID_DECISION_REASON");
  }

  private long integer(Object value) {
    if (value instanceof Number number) {
      try {
        return new java.math.BigDecimal(number.toString()).longValueExact();
      } catch (ArithmeticException | NumberFormatException exception) {
        throw invalid("INVALID_DECISION_VERSION");
      }
    }
    throw invalid("INVALID_DECISION_VERSION");
  }

  private Instant instant(Object value) {
    if (value instanceof Instant instant) return instant;
    if (value instanceof String text) {
      try {
        return Instant.parse(text);
      } catch (java.time.format.DateTimeParseException exception) {
        throw invalid("INVALID_DECISION_TIMESTAMP");
      }
    }
    return null;
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
