package com.hexaware.custodian_service.service.fees;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.accrual.schedule-enabled", havingValue = "true", matchIfMissing = true)
public class LendingFeeAccrualScheduler {
  private final LendingFeeAccrualService accruals;

  @Scheduled(cron = "${app.accrual.cron:0 10 0 * * *}", zone = "UTC")
  public void run() {
    accruals.runScheduledAccruals();
  }
}
