package com.hexaware.custodian_service.kafka.inbox;

import com.hexaware.custodian_service.kafka.consumer.MessageHandler;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.service.returns.SecuritiesReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityReturnSubmittedHandler implements MessageHandler {
  private final SecuritiesReturnService returns;

  @Override
  public String eventType() {
    return "SECURITY_RETURN_SUBMITTED";
  }

  @Override
  public void handle(EventEnvelope event) {
    returns.receiveReturnInstruction(event);
  }
}
