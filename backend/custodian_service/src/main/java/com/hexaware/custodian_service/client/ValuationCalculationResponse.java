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
public class ValuationCalculationResponse {
  private String calculationReference;
  private String securityIsin;
  private Long quantity;
  private BigDecimal marketPrice;
  private String currency;
  private String priceReference;
  private String priceSource;
  private Instant priceTimestamp;
  private Instant calculatedAt;
  private BigDecimal loanMarketValue;
  private BigDecimal requiredCashCollateral;
  private BigDecimal coveragePercentage;
  private BigDecimal cashHaircutPercentage;
  private BigDecimal creditReservationAmount;
}
