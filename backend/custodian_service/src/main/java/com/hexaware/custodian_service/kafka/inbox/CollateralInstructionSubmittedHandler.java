package com.hexaware.custodian_service.kafka.inbox;

import com.hexaware.custodian_service.kafka.consumer.MessageHandler;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.service.settlement.InitialSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollateralInstructionSubmittedHandler implements MessageHandler {
  private final InitialSettlementService settlements;

  @Override
  public String eventType() {
    return "COLLATERAL_INSTRUCTION_SUBMITTED";
  }

  @Override
  public void handle(EventEnvelope event) {
    settlements.receiveCashInstruction(event);
  }
}
