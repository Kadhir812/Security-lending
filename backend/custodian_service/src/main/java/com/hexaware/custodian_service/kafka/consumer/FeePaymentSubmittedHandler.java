package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.service.fees.LendingFeePaymentService;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeePaymentSubmittedHandler implements MessageHandler {
  private final LendingFeePaymentService payments;
  private final Clock clock;

  @Override
  public String eventType() {
    return "FEE_PAYMENT_SUBMITTED";
  }

  @Override
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String paymentReference = text(payload.get("paymentReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    if (!"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || integer(payload.get("payloadVersion")) != 1
        || !Objects.equals(event.getAggregateReference(), paymentReference)
        || !Objects.equals(event.getCorrelationId(), paymentReference)
        || !Objects.equals(event.getSenderOrganizationReference(), borrowerReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), custodianReference)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || paymentReference == null
        || borrowerReference == null
        || custodianReference == null) {
      throw new InvalidEnvelopeException("FEE_PAYMENT_ENVELOPE_MISMATCH");
    }
    payments.receiveSubmitted(event);
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private long integer(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw new InvalidEnvelopeException("INVALID_FEE_PAYMENT_PAYLOAD_VERSION");
    }
  }
}
