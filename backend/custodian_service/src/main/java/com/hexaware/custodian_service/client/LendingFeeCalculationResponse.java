package com.hexaware.custodian_service.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LendingFeeCalculationResponse {
  private String status;
  private String resultReference;
  private String calculationRequestReference;
  private String transactionReference;
  private LocalDate accrualDate;
  private Instant cutoff;
  private Long settledOutstandingQuantity;
  private BigDecimal marketPrice;
  private String priceReference;
  private String priceSource;
  private Instant priceTimestamp;
  private BigDecimal annualFeePercentage;
  private BigDecimal amount;
  private String currency;
  private String dayCountBasis;
  private String feeBasis;
  private String inputVersion;
  private String calculationRuleVersion;
  private String supersedesResultReference;
  private Instant calculatedAt;
  private String pendingReason;
}
