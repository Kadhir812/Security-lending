package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.entity.CalculationScheduleConfig;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.CalculationScheduleConfigRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CalculationScheduleService {
  private static final String SCHEDULE_KEY = "CUSTODIAN_ACCRUAL";
  private final CalculationScheduleConfigRepository schedules;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Map<String, Object> schedule() {
    return view(config());
  }

  @Transactional
  public Map<String, Object> updateInterval(int minutes) {
    validate(minutes);
    CalculationScheduleConfig schedule = lockedConfig();
    schedule.setIntervalMinutes(minutes);
    schedule.setNextRunAt(clock.instant().plusSeconds(minutes * 60L));
    schedule.setUpdatedAt(clock.instant());
    return view(schedules.save(schedule));
  }

  @Transactional
  public boolean claimDueRun() {
    CalculationScheduleConfig schedule = lockedConfig();
    var now = clock.instant();
    if (now.isBefore(schedule.getNextRunAt())) return false;
    schedule.setLastRunAt(now);
    schedule.setNextRunAt(now.plusSeconds(schedule.getIntervalMinutes() * 60L));
    schedule.setUpdatedAt(now);
    schedules.save(schedule);
    return true;
  }

  @Transactional
  public void recordRun(int postedCount) {
    CalculationScheduleConfig schedule = lockedConfig();
    schedule.setLastPostedCount(postedCount);
    schedule.setUpdatedAt(clock.instant());
    schedules.save(schedule);
  }

  @Transactional
  public void retryAfterFailure() {
    CalculationScheduleConfig schedule = lockedConfig();
    schedule.setNextRunAt(clock.instant().plusSeconds(schedule.getIntervalMinutes() * 60L));
    schedule.setUpdatedAt(clock.instant());
    schedules.save(schedule);
  }

  private CalculationScheduleConfig config() {
    return schedules.findById(SCHEDULE_KEY)
        .orElseThrow(() -> SetupException.unavailable("Calculation schedule configuration is missing"));
  }

  private CalculationScheduleConfig lockedConfig() {
    return schedules.findLocked(SCHEDULE_KEY)
        .orElseThrow(() -> SetupException.unavailable("Calculation schedule configuration is missing"));
  }

  private Map<String, Object> view(CalculationScheduleConfig schedule) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("intervalMinutes", schedule.getIntervalMinutes());
    result.put("nextRunAt", schedule.getNextRunAt());
    result.put("lastRunAt", schedule.getLastRunAt());
    result.put("lastPostedCount", schedule.getLastPostedCount());
    result.put("persisted", true);
    return result;
  }

  private void validate(int minutes) {
    if (minutes < 5 || minutes > 1440) {
      throw SetupException.invalid("Calculation interval must be between 5 and 1440 minutes");
    }
  }
}
