package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.CalculationScheduleConfig;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalculationScheduleConfigRepository
    extends JpaRepository<CalculationScheduleConfig, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select schedule from CalculationScheduleConfig schedule where schedule.scheduleKey = :key")
  Optional<CalculationScheduleConfig> findLocked(@Param("key") String key);
}
