package com.hexaware.custodian_service.service.fees;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.accrual.schedule-enabled", havingValue = "true", matchIfMissing = true)
public class LendingFeeAccrualScheduler {
  private final LendingFeeAccrualService accruals;
  private final RebateAccrualService rebates;
  private final CalculationScheduleService schedule;

  public LendingFeeAccrualScheduler(
      LendingFeeAccrualService accruals,
      RebateAccrualService rebates,
      CalculationScheduleService schedule) {
    this.accruals = accruals;
    this.rebates = rebates;
    this.schedule = schedule;
  }

  @Scheduled(fixedDelayString = "${app.accrual.check-interval-ms:30000}")
  public void run() {
    if (!schedule.claimDueRun()) return;
    try {
      int posted = accruals.runScheduledAccruals() + rebates.runScheduledAccruals();
      schedule.recordRun(posted);
    } catch (RuntimeException exception) {
      schedule.retryAfterFailure();
      throw exception;
    }
  }
}
