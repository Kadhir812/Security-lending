package com.hexaware.valuation_accural.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
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
@Table(name = "calculation_result")
public class CalculationResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "result_id")
  private Long resultId;

  @Column(name = "result_reference", nullable = false, unique = true, length = 36)
  private String resultReference;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "item_key", nullable = false, length = 150)
  private String itemKey;

  @Column(name = "result_type", nullable = false, length = 30)
  private String resultType;

  @Column(name = "transaction_reference", length = 80)
  private String transactionReference;

  @Column(name = "collateral_reference", length = 80)
  private String collateralReference;

  @Column(name = "calculation_date")
  private LocalDate calculationDate;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(precision = 20, scale = 6)
  private BigDecimal amount;

  @Column(name = "loan_value", precision = 20, scale = 2)
  private BigDecimal loanValue;

  @Column(name = "required_collateral", precision = 20, scale = 2)
  private BigDecimal requiredCollateral;

  @Column(name = "adjusted_collateral", precision = 20, scale = 2)
  private BigDecimal adjustedCollateral;

  @Column(name = "coverage_pct", precision = 12, scale = 6)
  private BigDecimal coveragePct;

  @Column(name = "result_schema_version", nullable = false)
  private Integer resultSchemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "json")
  private Map<String, Object> details;

  @Column(name = "supersedes_result_reference", length = 36)
  private String supersedesResultReference;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
