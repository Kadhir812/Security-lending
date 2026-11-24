package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "calculation_schedule_config")
public class CalculationScheduleConfig {
  @Id
  @Column(name = "schedule_key", nullable = false, length = 40)
  private String scheduleKey;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "interval_minutes", nullable = false)
  private Integer intervalMinutes;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "next_run_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant nextRunAt;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "last_run_at", columnDefinition = "datetime(6)")
  private Instant lastRunAt;

  @Column(name = "last_posted_count", nullable = false)
  private Integer lastPostedCount;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant updatedAt;
}
