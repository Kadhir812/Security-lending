package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.entity.OutboxEvent;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.repository.OutboxEventRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollateralReportIssuedHandler implements MessageHandler {
  private final LoanRequestRepository requests;
  private final OutboxEventRepository outboxEvents;
  private final LoanRequestFixMessageBuilder fixMessages;
  private final Clock clock;

  @Override
  public String eventType() {
    return "COLLATERAL_REPORT_ISSUED";
  }

  @Override
  public void handle(EventEnvelope event) {
    if (!"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || !Objects.equals(event.getAggregateVersion(), 0L)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())) {
      throw invalid("FIX_BA_EVENT_METADATA_INVALID");
    }

    String wireMessage = payloadMessage(event.getPayload());
    fixMessages.validateCollateralReportForEnvelope(event, wireMessage);
    var report = fixMessages.parseCollateralReport(wireMessage);
    String requestReference = field(report, 9001);
    String mslaReference = field(report, 9002);
    String inquiryId = field(report, 909);

    var loanRequest =
        requests
            .findByRequestReference(requestReference)
            .orElseThrow(() -> invalid("FIX_BA_LOAN_REQUEST_NOT_FOUND"));
    if (!Objects.equals(loanRequest.getMslaReference(), mslaReference)
        || !Objects.equals(loanRequest.getSettlementCurrency(), field(report, 918))
        || !Objects.equals(
            loanRequest.getBorrowerOrganizationReference(),
            event.getReceiverOrganizationReference())
        || !Objects.equals(
            loanRequest.getCustodianOrganizationReference(),
            event.getSenderOrganizationReference())) {
      throw invalid("FIX_BA_REQUEST_MSLA_OR_COUNTERPARTY_MISMATCH");
    }

    OutboxEvent inquiry =
        outboxEvents
            .findByEventTypeAndCorrelationIdAndAggregateReference(
                "COLLATERAL_INQUIRY_SUBMITTED", inquiryId, inquiryId)
            .orElseThrow(() -> invalid("FIX_BA_ORIGINAL_INQUIRY_NOT_FOUND"));
    validateOriginalInquiry(inquiry, inquiryId, requestReference, mslaReference, event);
  }

  private void validateOriginalInquiry(
      OutboxEvent inquiry,
      String inquiryId,
      String requestReference,
      String mslaReference,
      EventEnvelope reportEvent) {
    if (!"BORROWER".equals(inquiry.getProducerService())
        || !"CUSTODIAN".equals(inquiry.getDestinationService())
        || !Objects.equals(
            inquiry.getSenderOrganizationReference(),
            reportEvent.getReceiverOrganizationReference())
        || !Objects.equals(
            inquiry.getReceiverOrganizationReference(),
            reportEvent.getSenderOrganizationReference())) {
      throw invalid("FIX_BA_ORIGINAL_INQUIRY_PARTIES_MISMATCH");
    }
    String inquiryFix = payloadMessage(inquiry.getPayload());
    var parsedInquiry = fixMessages.parseCollateralInquiryAndValidate(inquiryFix);
    var header = parsedInquiry.getHeader();
    if (!Objects.equals(inquiry.getSenderOrganizationReference(), field(header, 49))
        || !Objects.equals(inquiry.getReceiverOrganizationReference(), field(header, 56))
        || !Objects.equals(
            Math.toIntExact(inquiry.getAggregateVersion() + 1),
            Integer.valueOf(field(header, 34)))
        || !Objects.equals(inquiryId, inquiry.getCorrelationId())
        || !Objects.equals(inquiryId, inquiry.getAggregateReference())
        || !inquiryId.equals(field(parsedInquiry, 909))
        || !requestReference.equals(field(parsedInquiry, 9001))
        || !mslaReference.equals(field(parsedInquiry, 9002))) {
      throw invalid("FIX_BA_ORIGINAL_INQUIRY_LINKAGE_MISMATCH");
    }
  }

  private static String payloadMessage(Map<String, Object> payload) {
    Object content = payload == null ? null : payload.get("payload");
    Object value =
        content instanceof Map<?, ?> nested
            ? nested.get("fixMessage")
            : payload == null ? null : payload.get("fixMessage");
    if (!(value instanceof String message) || message.isBlank()) {
      throw invalid("FIX_BA_MESSAGE_MISSING");
    }
    return message;
  }

  private static String field(quickfix.FieldMap message, int tag) {
    try {
      return message.getString(tag);
    } catch (quickfix.FieldNotFound exception) {
      throw invalid("FIX_BA_REQUIRED_FIELD_MISSING");
    }
  }

  private static InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
