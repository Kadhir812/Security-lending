package com.hexaware.borrower_service.service;

import com.hexaware.borrower_service.dto.request.LocateRequestSubmission;
import com.hexaware.borrower_service.dto.response.LocateRequestResponse;
import com.hexaware.borrower_service.entity.LocateRequest;
import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.enums.SubmissionStatus;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocateRequestService {
  private final LocateRequestRepository locates;
  private final LoanRequestSubmissionValidator validator;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  @Transactional
  public LocateRequestResponse submit(
      Authentication authentication,
      String locateReference,
      LocateRequestSubmission submission) {
    var borrower = validator.requireBorrower(authentication);
    validator.validateIdempotencyKey(locateReference);
    LocateRequest existing =
        locates.findByLocateReferenceAndBorrowerOrganizationReference(
            locateReference, borrower.getOrganizationReference()).orElse(null);
    if (existing != null) {
      if (!existing.getMslaReference().equals(submission.mslaReference())
          || !existing.getSecurityIsin().equals(submission.securityIsin())
          || !Objects.equals(existing.getQuantity(), submission.quantity())) {
        throw BorrowerException.conflict("Locate idempotency key was reused with different inputs");
      }
      return response(existing);
    }

    var validated =
        validator.validateLocate(authentication, submission.mslaReference(), submission.securityIsin());
    if (!validated.borrower().getOrganizationReference().equals(borrower.getOrganizationReference())) {
      throw BorrowerException.conflict("Borrower membership changed");
    }

    var now = clock.instant();
    LocateRequest locate =
        locates.saveAndFlush(
            LocateRequest.builder()
                .borrowerOrganizationReference(borrower.getOrganizationReference())
                .custodianOrganizationReference(validated.custodianOrganizationReference())
                .submissionStatus(SubmissionStatus.QUEUED.name())
                .decisionSourceVersion(0L)
                .version(0L)
                .locateReference(locateReference)
                .mslaReference(submission.mslaReference())
                .securityIsin(submission.securityIsin())
                .quantity(submission.quantity())
                .createdBy(borrower.getUserId())
                .requestTime(now)
                .status(LocateStatus.PENDING)
                .build());

    EventEnvelope event =
        EventEnvelope.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("LOCATE_REQUEST_SUBMITTED")
            .schemaVersion(1)
            .producerService("BORROWER")
            .destinationService("CUSTODIAN")
            .correlationId(locateReference)
            .aggregateReference(locateReference)
            .aggregateVersion(0L)
            .occurredAt(now)
            .senderOrganizationReference(borrower.getOrganizationReference())
            .receiverOrganizationReference(validated.custodianOrganizationReference())
            .payload(
                Map.of(
                    "payloadVersion", 1,
                    "locateReference", locateReference,
                    "mslaReference", submission.mslaReference(),
                    "securityIsin", submission.securityIsin(),
                    "quantity", submission.quantity(),
                    "borrowerOrganizationReference", borrower.getOrganizationReference(),
                    "custodianOrganizationReference", validated.custodianOrganizationReference(),
                    "submittedByIssuer", borrower.getToken().getClaimAsString("iss"),
                    "submittedBySubject", borrower.getToken().getSubject()))
            .build();
    outgoing.enqueue("LOCATE_REQUEST", event);
    return response(locate);
  }

  @Transactional(readOnly = true)
  public List<LocateRequestResponse> list(Authentication authentication) {
    var borrower = validator.requireBorrower(authentication);
    return locates
        .findByBorrowerOrganizationReferenceOrderByRequestTimeDesc(
            borrower.getOrganizationReference())
        .stream()
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public LocateRequestResponse get(Authentication authentication, String locateReference) {
    var borrower = validator.requireBorrower(authentication);
    return locates
        .findByLocateReferenceAndBorrowerOrganizationReference(
            locateReference, borrower.getOrganizationReference())
        .map(this::response)
        .orElseThrow(() -> BorrowerException.missing("Locate request not found"));
  }

  private LocateRequestResponse response(LocateRequest locate) {
    return new LocateRequestResponse(
        locate.getLocateReference(),
        locate.getMslaReference(),
        locate.getSecurityIsin(),
        locate.getQuantity(),
        locate.getApprovedQuantity(),
        locate.getStatus(),
        locate.getResponseReason(),
        SubmissionStatus.valueOf(locate.getSubmissionStatus()),
        locate.getRequestTime(),
        locate.getRespondedAt(),
        locate.getExpiryTime());
  }
}
