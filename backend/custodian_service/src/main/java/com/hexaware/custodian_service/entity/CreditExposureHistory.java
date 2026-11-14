package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "credit_exposure_history")
public class CreditExposureHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "exposure_history_id")
  private Long exposureHistoryId;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "credit_limit_id", nullable = false)
  private Long creditLimitId;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "valuation_result_reference", nullable = false, unique = true, length = 36)
  private String valuationResultReference;

  @Column(name = "idempotency_key", nullable = false, length = 80)
  private String idempotencyKey;

  @Column(name = "input_hash", nullable = false, length = 64)
  private String inputHash;

  @Column(name = "old_used_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal oldUsedAmount;

  @Column(name = "new_used_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal newUsedAmount;

  @Column(name = "old_available_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal oldAvailableAmount;

  @Column(name = "new_available_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal newAvailableAmount;

  @Column(name = "breach_created", nullable = false)
  private boolean breachCreated;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt;
}
