package com.hexaware.valuation_accural.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "calculation_run")
public class CalculationRun {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "run_id")
  private Long runId;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "input_schema_version", nullable = false)
  private Integer inputSchemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "json")
  private Map<String, Object> inputs;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "price_snapshots", nullable = false, columnDefinition = "json")
  private Map<String, Object> priceSnapshots;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rule_parameters", nullable = false, columnDefinition = "json")
  private Map<String, Object> ruleParameters;

  @Column(name = "implementation_digest", nullable = false, length = 128)
  private String implementationDigest;

  @Column(name = "run_reference", nullable = false, unique = true, length = 36)
  private String runReference;

  @Column(name = "requester_service", nullable = false, length = 80)
  private String requesterService;

  @Column(name = "request_reference", nullable = false, length = 80)
  private String requestReference;

  @Column(name = "request_payload_hash", nullable = false, length = 64)
  private String requestPayloadHash;

  @Column(name = "correlation_reference", nullable = false, length = 80)
  private String correlationReference;

  @Column(name = "calculation_type", nullable = false, length = 30)
  private String calculationType;

  @Column(nullable = false, length = 20)
  private String purpose;

  @Column(name = "transaction_reference", length = 80)
  private String transactionReference;

  @Column(name = "borrower_organization_reference", nullable = false, length = 80)
  private String borrowerOrganizationReference;

  @Column(name = "input_contract_version")
  private Long inputContractVersion;

  @Column(name = "valuation_at", nullable = false)
  private Instant valuationAt;

  @Column(name = "period_start")
  private LocalDate periodStart;

  @Column(name = "period_end")
  private LocalDate periodEnd;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "rule_version", nullable = false, length = 80)
  private String ruleVersion;

  @Column(name = "supersedes_run_reference", length = 36)
  private String supersedesRunReference;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private Integer attemptCount;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;
}
