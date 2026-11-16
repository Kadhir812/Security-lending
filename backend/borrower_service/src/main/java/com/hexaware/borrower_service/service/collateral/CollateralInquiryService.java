package com.hexaware.borrower_service.service.collateral;

import com.hexaware.borrower_service.dto.response.CollateralInquiryResponse;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollateralInquiryService {
  private final LoanRequestRepository requests;
  private final LoanRequestSubmissionValidator validator;
  private final OutgoingEventService outgoing;
  private final LoanRequestFixMessageBuilder fixMessages;
  private final Clock clock;

  @Transactional
  public CollateralInquiryResponse submit(
      Authentication authentication, String requestReference) {
    var borrower = validator.requireBorrower(authentication);
    var loanRequest =
        requests
            .findByRequestReferenceAndBorrowerOrganizationReference(
                requestReference, borrower.getOrganizationReference())
            .orElseThrow(() -> BorrowerException.missing("Loan request not found"));

    String inquiryId = UUID.randomUUID().toString();
    var event =
        EventEnvelope.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("COLLATERAL_INQUIRY_SUBMITTED")
            .schemaVersion(1)
            .producerService("BORROWER")
            .destinationService("CUSTODIAN")
            .correlationId(inquiryId)
            .aggregateReference(inquiryId)
            .aggregateVersion(0L)
            .occurredAt(clock.instant())
            .senderOrganizationReference(borrower.getOrganizationReference())
            .receiverOrganizationReference(loanRequest.getCustodianOrganizationReference())
            .payload(Map.of())
            .build();
    event.setPayload(
        Map.of(
            "fixMessage",
            fixMessages.buildCollateralInquiry(
                event,
                inquiryId,
                loanRequest.getRequestReference(),
                loanRequest.getMslaReference())));
    outgoing.enqueue("COLLATERAL_INQUIRY", event);

    return new CollateralInquiryResponse(
        inquiryId, loanRequest.getRequestReference(), loanRequest.getMslaReference(), "QUEUED");
  }
}
