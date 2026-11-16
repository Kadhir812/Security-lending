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
public class LendingFeeCalculationRequest {
  private String calculationRequestReference;
  private String transactionReference;
  private String securityIsin;
  private LocalDate accrualDate;
  private Instant cutoff;
  private Long settledOutstandingQuantity;
  private BigDecimal annualFeePercentage;
  private String currency;
  private String dayCountBasis;
  private String inputVersion;
  private String borrowerOrganizationReference;
  private String custodianOrganizationReference;
  private String supersedesResultReference;
}
