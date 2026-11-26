package com.hexaware.custodian_service.client;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MtmCalculationRequest {
  private String securityIsin;
  private String calculationRequestReference;
  private Long outstandingQuantity;
  private BigDecimal coveragePercentage;
  private BigDecimal confirmedCashCollateral;
  private String currency;
  private String custodianOrganizationReference;
  private String borrowerOrganizationReference;
  private String transactionReference;
  private Instant inputAsOf;
  private Long inputVersion;
}
