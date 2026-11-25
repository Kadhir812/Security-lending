package com.hexaware.custodian_service.service.margin;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mtm.schedule-enabled", havingValue = "true", matchIfMissing = true)
public class MarginCallScheduler {
  private final MarginCallService service;

  @Scheduled(fixedDelayString = "${app.mtm.poll-interval:PT1M}")
  public void run() {
    service.runScheduledMtm();
    service.markOverdueCalls();
  }
}
