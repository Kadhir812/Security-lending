package com.hexaware.custodian_service.kafka.inbox;

import com.hexaware.custodian_service.kafka.consumer.MessageHandler;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.service.collateral.FinalCollateralReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinalCollateralReleaseRequestedHandler implements MessageHandler {
  private final FinalCollateralReleaseService releases;

  @Override
  public String eventType() {
    return "FINAL_COLLATERAL_RELEASE_REQUESTED";
  }

  @Override
  public void handle(EventEnvelope event) {
    releases.receiveRequest(event);
  }
}
