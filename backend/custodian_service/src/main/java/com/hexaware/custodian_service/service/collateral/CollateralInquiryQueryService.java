package com.hexaware.custodian_service.service.collateral;

import com.hexaware.custodian_service.dto.response.CollateralInquiryResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.entity.InboxMessage;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.config.MessagingProperties;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.quickfix.LoanRequestFixMessageParser;
import com.hexaware.custodian_service.repository.InboxMessageRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CollateralInquiryQueryService {
  private final LendingSetupAccess access;
  private final InboxMessageRepository inbox;
  private final OutboxEventRepository outbox;
  private final MessagingProperties messaging;
  private final LoanRequestFixMessageParser fixMessages;
  private final ObjectMapper mapper;

  @Transactional(readOnly = true)
  public PageResponse<CollateralInquiryResponse> list(
      Authentication authentication, int page, int size) {
    var custodian = access.actor(authentication, false);
    if (page < 0 || size < 1 || size > 100) {
      throw SetupException.invalid("Invalid page or size");
    }
    var result = inbox.findProcessedCollateralInquiries(
        messaging.getConsumerName(), custodian.getOrganizationReference(), PageRequest.of(page, size));
    return PageResponse.from(result.map(this::response));
  }

  private CollateralInquiryResponse response(InboxMessage row) {
    Map<String, Object> saved = row.getPayload();
    EventEnvelope event = mapper.convertValue(saved, EventEnvelope.class);
    var inquiry = fixMessages.parseCollateralInquiry(event);
    var report = outbox.findByCorrelationIdAndEventType(inquiry.inquiryId(), "COLLATERAL_REPORT_ISSUED").orElse(null);
    return new CollateralInquiryResponse(
        inquiry.inquiryId(),
        inquiry.requestReference(),
        inquiry.mslaReference(),
        inquiry.borrowerOrganizationReference(),
        inquiry.custodianOrganizationReference(),
        row.getReceivedAt(),
        row.getStatus().name(),
        report == null ? null : report.getEventId(),
        report == null ? "NOT_ISSUED" : report.getStatus().name(),
        report == null ? null : report.getPublishedAt());
  }
}
