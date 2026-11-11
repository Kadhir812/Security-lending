package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.kafka.event.CollateralInquirySubmittedPayload;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.repository.CollateralHaircutRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import com.hexaware.custodian_service.quickfix.CollateralReportFixMessageBuilder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import com.hexaware.custodian_service.quickfix.LoanRequestFixMessageParser;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollateralInquirySubmittedHandler implements MessageHandler {
  private final LoanRequestFixMessageParser fixMessages;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final CollateralHaircutRepository haircuts;
  private final OutboxEventRepository outboxEvents;
  private final OutgoingEventService outgoing;
  private final CollateralReportFixMessageBuilder reports;
  private final Clock clock;

  @Override
  public String eventType() {
    return "COLLATERAL_INQUIRY_SUBMITTED";
  }

  @Override
  public void handle(EventEnvelope event) {
    CollateralInquirySubmittedPayload inquiry = fixMessages.parseCollateralInquiry(event);
    if (event.getSchemaVersion() != 1
        || event.getAggregateVersion() == null
        || event.getAggregateVersion() != 0L) {
      throw invalid("FIX_BB_EVENT_VERSION_INVALID");
    }

    var loanRequest =
        requests
            .findByRequestReference(inquiry.requestReference())
            .orElseThrow(() -> invalid("FIX_BB_LOAN_REQUEST_NOT_FOUND"));
    var agreement =
        agreements
            .findById(loanRequest.getMslaId())
            .orElseThrow(() -> invalid("FIX_BB_MSLA_NOT_FOUND"));
    if (!agreement.getMslaReference().equals(inquiry.mslaReference())) {
      throw invalid("FIX_BB_MSLA_DOES_NOT_MATCH_LOAN_REQUEST");
    }
    var mandate =
        mandates
            .findById(agreement.getMandateId())
            .orElseThrow(() -> invalid("FIX_BB_MANDATE_NOT_FOUND"));
    var borrower =
        organizations
            .findById(agreement.getBorrowerOrgId())
            .orElseThrow(() -> invalid("FIX_BB_BORROWER_NOT_FOUND"));
    var custodian =
        organizations
            .findById(mandate.getCustodianOrgId())
            .orElseThrow(() -> invalid("FIX_BB_CUSTODIAN_NOT_FOUND"));
    if (!Objects.equals(
            borrower.getOrganizationReference(), inquiry.borrowerOrganizationReference())
        || !Objects.equals(
            custodian.getOrganizationReference(), inquiry.custodianOrganizationReference())
        || !Objects.equals(
            borrower.getOrganizationReference(), event.getSenderOrganizationReference())
        || !Objects.equals(
            custodian.getOrganizationReference(), event.getReceiverOrganizationReference())) {
      throw invalid("FIX_BB_COUNTERPARTY_OR_OWNERSHIP_MISMATCH");
    }
    if (agreement.getCollateralType() != CollateralType.CASH
        || agreement.getBaseCurrency() == null
        || agreement.getCollateralCoveragePct() == null
        || agreement.getCollateralCoveragePct().compareTo(BigDecimal.valueOf(100)) < 0) {
      throw invalid("FIX_BB_AGREEMENT_TERMS_UNSUPPORTED");
    }

    var effectiveHaircuts =
        haircuts.findByMslaIdOrderByEffectiveFromDescHaircutIdDesc(agreement.getMslaId()).stream()
            .filter(rule -> rule.getCollateralType() == CollateralType.CASH)
            .filter(rule -> agreement.getBaseCurrency().equals(rule.getCurrency()))
            .filter(rule -> !rule.getEffectiveFrom().isAfter(clock.instant()))
            .filter(rule -> rule.getEffectiveTo() == null || rule.getEffectiveTo().isAfter(clock.instant()))
            .toList();
    if (effectiveHaircuts.size() != 1
        || effectiveHaircuts.getFirst().getHaircutPercentage().compareTo(BigDecimal.ZERO) != 0) {
      throw invalid("FIX_BB_CASH_HAIRCUT_NOT_ZERO_OR_UNAMBIGUOUS");
    }

    String responseEventId = responseEventId(inquiry.inquiryId());
    OutboxEvent existingResponse = outboxEvents.findByEventId(responseEventId).orElse(null);
    if (existingResponse != null) {
      if (!"COLLATERAL_REPORT_ISSUED".equals(existingResponse.getEventType())
          || !inquiry.requestReference().equals(existingResponse.getAggregateReference())
          || !inquiry.inquiryId().equals(existingResponse.getCorrelationId())) {
        throw invalid("FIX_BB_RESPONSE_ID_REUSED");
      }
      return;
    }

    var reportEvent =
        EventEnvelope.builder()
            .eventId(responseEventId)
            .eventType("COLLATERAL_REPORT_ISSUED")
            .schemaVersion(1)
            .producerService("CUSTODIAN")
            .destinationService("BORROWER")
            .correlationId(inquiry.inquiryId())
            .aggregateReference(inquiry.requestReference())
            .aggregateVersion(0L)
            .occurredAt(clock.instant())
            .senderOrganizationReference(custodian.getOrganizationReference())
            .receiverOrganizationReference(borrower.getOrganizationReference())
            .payload(Map.of())
            .build();
    String reportMessage =
        reports.build(
            reportEvent,
            responseEventId,
            inquiry.inquiryId(),
            inquiry.requestReference(),
            agreement.getMslaReference(),
            agreement.getBaseCurrency(),
            effectiveHaircuts.getFirst().getHaircutPercentage(),
            agreement.getCollateralCoveragePct());
    reportEvent.setPayload(Map.of("fixMessage", reportMessage));
    outgoing.enqueue("COLLATERAL_REPORT", reportEvent);
  }

  private static String responseEventId(String inquiryId) {
    return UUID.nameUUIDFromBytes(
            ("COLLATERAL_REPORT:" + inquiryId).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private static InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
