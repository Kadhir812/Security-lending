package com.hexaware.custodian_service.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Data
public class RebateCalculationResponse {
  private String status;
  private String resultReference;
  private String calculationRequestReference;
  private String transactionReference;
  private LocalDate accrualDate;
  private Instant cutoff;
  private BigDecimal settledCashCollateral;
  private BigDecimal annualRebatePercentage;
  private BigDecimal amount;
  private String currency;
  private String dayCountBasis;
  private String rebateBasis;
  private String inputVersion;
  private String calculationRuleVersion;
  private String supersedesResultReference;
  private Instant calculatedAt;
}
