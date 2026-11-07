package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.LoanContractStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "loan_contract")
public class LoanContract {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "transaction_reference", nullable = false, unique = true, length = 80)
  private String transactionReference;

  @Column(name = "loan_request_id", nullable = false, unique = true)
  private Long loanRequestId;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Builder.Default
  @Column(name = "delivered_quantity", nullable = false)
  private Long deliveredQuantity = 0L;

  @Builder.Default
  @Column(name = "returned_quantity", nullable = false)
  private Long returnedQuantity = 0L;

  @Column(name = "outstanding_quantity", insertable = false, updatable = false)
  private Long outstandingQuantity;

  @Column(name = "settlement_currency", nullable = false, length = 3)
  private String settlementCurrency;

  @Column(name = "agreed_annual_fee_pct", nullable = false, precision = 10, scale = 6)
  private BigDecimal agreedAnnualFeePct;

  @Builder.Default
  @Column(name = "agreed_annual_rebate_pct", nullable = false, precision = 10, scale = 6)
  private BigDecimal agreedAnnualRebatePct = BigDecimal.ZERO;

  @Builder.Default
  @Column(name = "day_count_basis", nullable = false)
  private String dayCountBasis = "ACT_365";

  @Column(name = "collateral_coverage_pct", nullable = false, precision = 9, scale = 4)
  private BigDecimal collateralCoveragePct;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "collateral_terms", nullable = false, columnDefinition = "json")
  private Map<String, Object> collateralTerms;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "accepted_valuation_snapshot", columnDefinition = "json")
  private Map<String, Object> acceptedValuationSnapshot;

  @Column(name = "latest_valuation_result_reference", length = 36)
  private String latestValuationResultReference;

  @Column(name = "latest_market_price", precision = 20, scale = 6)
  private BigDecimal latestMarketPrice;

  @Column(name = "latest_loan_value", precision = 20, scale = 2)
  private BigDecimal latestLoanValue;

  @Column(name = "latest_required_collateral", precision = 20, scale = 2)
  private BigDecimal latestRequiredCollateral;

  @Column(name = "latest_adjusted_collateral", precision = 20, scale = 2)
  private BigDecimal latestAdjustedCollateral;

  @Column(name = "latest_coverage_pct", precision = 12, scale = 6)
  private BigDecimal latestCoveragePct;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "latest_valuation_at", columnDefinition = "datetime(6)")
  private Instant latestValuationAt;

  @Builder.Default
  @Column(name = "reserved_credit_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal reservedCreditAmount = BigDecimal.ZERO;

  @Builder.Default
  @Column(name = "used_credit_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal usedCreditAmount = BigDecimal.ZERO;

  @Column(name = "settlement_calendar_version", nullable = false, length = 80)
  private String settlementCalendarVersion;

  @Column(name = "trade_date", nullable = false)
  private LocalDate tradeDate;

  @Column(name = "settlement_date", nullable = false)
  private LocalDate settlementDate;

  @Column(name = "maturity_date")
  private LocalDate maturityDate;

  @Column(name = "recall_conditions", length = 500)
  private String recallConditions;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "contract_status", nullable = false)
  private LoanContractStatus contractStatus = LoanContractStatus.APPROVED;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "contract_created_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant contractCreatedAt;
}
