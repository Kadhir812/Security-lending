package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "credit_limit_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLimitHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "history_id")
  private Long historyId;

  @Column(name = "credit_limit_id", nullable = false)
  private Long creditLimitId;

  @Column(name = "old_limit", nullable = false, precision = 20, scale = 2)
  private BigDecimal oldLimit;

  @Column(name = "new_limit", nullable = false, precision = 20, scale = 2)
  private BigDecimal newLimit;

  @Column(name = "reason", nullable = false, length = 500)
  private String reason;

  @Column(name = "changed_by", nullable = false)
  private Long changedBy;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "changed_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant changedAt;
}
