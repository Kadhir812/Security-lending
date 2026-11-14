package com.hexaware.custodian_service.client;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class MtmCalculationResponse {
  private String calculationReference;
  private String calculationRequestReference;
  private String transactionReference;
  private String securityIsin;
  private Long outstandingQuantity;
  private BigDecimal marketPrice;
  private String currency;
  private String priceReference;
  private String priceSource;
  private Instant priceTimestamp;
  private Instant calculatedAt;
  private BigDecimal loanMarketValue;
  private BigDecimal requiredCashCollateral;
  private BigDecimal confirmedCashCollateral;
  private BigDecimal shortfallAmount;
  private BigDecimal coveragePercentage;
  private BigDecimal cashHaircutPercentage;
  private String calculationRuleVersion;
  private Instant inputAsOf;
  private Long inputVersion;
  private String custodianOrganizationReference;
  private String borrowerOrganizationReference;
}
