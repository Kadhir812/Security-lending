package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.service.fees.RebateProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RebatePaymentUpdatedHandler implements MessageHandler {
  private final RebateProjectionService projections;

  @Override
  public String eventType() {
    return "REBATE_PAYMENT_UPDATED";
  }

  @Override
  public void handle(EventEnvelope event) {
    projections.applyPayment(event);
  }
}
