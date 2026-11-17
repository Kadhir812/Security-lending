package com.hexaware.custodian_service.service.fees;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CashRebateAccrualScheduler {
  private final CashRebateAccrualService accruals;

  @Scheduled(cron = "${app.accrual.cron:0 10 0 * * *}", zone = "Asia/Kolkata")
  public void run() {
    accruals.runScheduledAccruals();
  }
}
