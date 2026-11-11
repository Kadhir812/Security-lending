package com.hexaware.borrower_service.service.collateral;

import com.hexaware.borrower_service.dto.response.CollateralReportResponse;
import com.hexaware.borrower_service.entity.InboxMessage;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.kafka.config.MessagingProperties;
import com.hexaware.borrower_service.repository.InboxMessageRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.enums.InboxMessageStatus;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollateralReportService {
  private final LoanRequestRepository requests;
  private final InboxMessageRepository inboxMessages;
  private final MessagingProperties messaging;
  private final LoanRequestSubmissionValidator validator;
  private final LoanRequestFixMessageBuilder fixMessages;

  @Transactional(readOnly = true)
  public List<CollateralReportResponse> list(Authentication authentication, String requestReference) {
    var borrower = validator.requireBorrower(authentication);
    requests
        .findByRequestReferenceAndBorrowerOrganizationReference(
            requestReference, borrower.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Loan request not found"));

    return inboxMessages
        .findByConsumerNameAndEventTypeAndAggregateReferenceAndStatusOrderByReceivedAtAsc(
            messaging.getConsumerName(),
            "COLLATERAL_REPORT_ISSUED",
            requestReference,
            InboxMessageStatus.PROCESSED)
        .stream()
        .map(this::response)
        .toList();
  }

  private CollateralReportResponse response(InboxMessage inbox) {
    var payload = inbox.getPayload();
    Object content = payload == null ? null : payload.get("payload");
    Object rawMessage =
        content instanceof java.util.Map<?, ?> nested
            ? nested.get("fixMessage")
            : payload == null ? null : payload.get("fixMessage");
    if (!(rawMessage instanceof String wireMessage)) {
      throw new IllegalStateException("Persisted FIX BA inbox message is missing fixMessage");
    }
    var message = fixMessages.parseCollateralReport(wireMessage);
    try {
      return new CollateralReportResponse(
          message.getString(908),
          message.getString(909),
          message.getString(9001),
          message.getString(9002),
          "UNASSIGNED",
          message.getString(9010),
          message.getString(918),
          message.getString(9011),
          message.getString(9012),
          inbox.getReceivedAt());
    } catch (quickfix.FieldNotFound exception) {
      throw new IllegalStateException("Persisted FIX BA inbox message is incomplete", exception);
    }
  }
}
