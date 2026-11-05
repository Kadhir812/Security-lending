package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.service.fees.FeeProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeePaymentUpdatedHandler implements MessageHandler {
  private final FeeProjectionService projections;

  @Override
  public String eventType() {
    return "FEE_PAYMENT_UPDATED";
  }

  @Override
  public void handle(EventEnvelope event) {
    projections.applyPayment(event, eventType());
  }
}
