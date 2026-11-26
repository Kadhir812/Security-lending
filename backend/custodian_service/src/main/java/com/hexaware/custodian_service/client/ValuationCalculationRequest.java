package com.hexaware.custodian_service.client;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationCalculationRequest {
  private String securityIsin;
  private Long quantity;
  private BigDecimal coveragePercentage;
  private String currency;
  private String custodianOrganizationReference;
}
