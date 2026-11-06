package com.hexaware.borrower_service.service.loan;

import com.hexaware.borrower_service.dto.request.LoanRequestSubmission;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.entity.LocateRequest;
import com.hexaware.borrower_service.entity.User;
import com.hexaware.borrower_service.enums.LoanRequestStatus;
import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.enums.SubmissionStatus;
import com.hexaware.borrower_service.enums.UserStatus;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import com.hexaware.borrower_service.repository.UserRepository;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanRequestWriter {
  private final LoanRequestRepository requests;
  private final LocateRequestRepository locates;
  private final UserRepository users;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  @Transactional
  public LoanRequest submit(
      Long userId,
      String borrowerReference,
      String issuer,
      String subject,
      String requestReference,
      LoanRequestSubmission submission,
      String custodianReference,
      Long locateId) {
    User user =
        users
            .findLockedByUserId(userId)
            .orElseThrow(() -> BorrowerException.conflict("Borrower membership is no longer active"));
    if (!user.getOrganizationReference().equals(borrowerReference)) {
      throw BorrowerException.conflict("Borrower membership changed");
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw BorrowerException.conflict("Borrower membership is no longer active");
    }

    var existing =
        requests
            .findByRequestReference(requestReference)
            .orElse(null);
    if (existing != null) {
      if (!existing.getBorrowerOrganizationReference().equals(borrowerReference)
          || !matches(existing, submission, locateId)) {
        throw BorrowerException.conflict("Idempotency key was already used for another request");
      }
      return existing;
    }

    LoanRequest request =
        requests.saveAndFlush(
            LoanRequest.builder()
                .borrowerOrganizationReference(borrowerReference)
                .custodianOrganizationReference(custodianReference)
                .submissionStatus(SubmissionStatus.QUEUED)
                .decisionSourceVersion(0L)
                .requestReference(requestReference)
                .mslaReference(submission.mslaReference())
                .locateId(locateId)
                .securityIsin(submission.securityIsin())
                .quantity(submission.quantity())
                .requestedSettlementDate(submission.requestedSettlementDate())
                .settlementCurrency(submission.settlementCurrency())
                .createdBy(userId)
                .requestedAt(clock.instant())
                .status(LoanRequestStatus.PENDING)
                .build());

    Instant occurredAt = clock.instant();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("requestReference", requestReference);
    payload.put("borrowerOrganizationReference", borrowerReference);
    payload.put("custodianOrganizationReference", custodianReference);
    payload.put("mslaReference", submission.mslaReference());
    payload.put("locateReference", locateReference(locateId));
    payload.put("securityIsin", submission.securityIsin());
    payload.put("quantity", submission.quantity());
    payload.put("requestedSettlementDate", submission.requestedSettlementDate().toString());
    payload.put("settlementCurrency", submission.settlementCurrency());
    payload.put("submittedByIssuer", issuer);
    payload.put("submittedBySubject", subject);

    outgoing.enqueue(
        "LOAN_REQUEST",
        EventEnvelope.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("LOAN_REQUEST_SUBMITTED")
            .schemaVersion(1)
            .producerService("BORROWER")
            .destinationService("CUSTODIAN")
            .correlationId(requestReference)
            .aggregateReference(requestReference)
            .aggregateVersion(request.getVersion() == null ? 0L : request.getVersion())
            .occurredAt(occurredAt)
            .senderOrganizationReference(borrowerReference)
            .receiverOrganizationReference(custodianReference)
            .payload(payload)
            .build());
    return request;
  }

  private boolean matches(LoanRequest existing, LoanRequestSubmission submission, Long locateId) {
    return existing.getMslaReference().equals(submission.mslaReference())
        && java.util.Objects.equals(existing.getLocateId(), locateId)
        && existing.getSecurityIsin().equals(submission.securityIsin())
        && existing.getQuantity().equals(submission.quantity())
        && existing.getRequestedSettlementDate().equals(submission.requestedSettlementDate())
        && java.util.Objects.equals(
            existing.getSettlementCurrency(), submission.settlementCurrency());
  }

  private String locateReference(Long locateId) {
    if (locateId == null) return null;
    return locates
        .findById(locateId)
        .map(LocateRequest::getLocateReference)
        .orElseThrow(() -> BorrowerException.conflict("Locate reference is no longer available"));
  }
}
