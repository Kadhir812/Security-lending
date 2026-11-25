package com.hexaware.valuation_accural.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class LoanApprovalCalculator {
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  public Amounts calculate(
      BigDecimal unitPrice, long quantity, BigDecimal coveragePercentage) {
    BigDecimal loanValue =
        unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    BigDecimal collateral =
        loanValue
            .multiply(coveragePercentage)
            .divide(ONE_HUNDRED, 2, RoundingMode.CEILING);
    return new Amounts(loanValue, collateral);
  }

  public record Amounts(BigDecimal loanMarketValue, BigDecimal requiredCashCollateral) {}

  public BigDecimal shortfall(
      BigDecimal requiredCashCollateral, BigDecimal confirmedCashCollateral) {
    if (requiredCashCollateral == null || confirmedCashCollateral == null
        || requiredCashCollateral.signum() < 0 || confirmedCashCollateral.signum() < 0) {
      throw new IllegalArgumentException("Invalid collateral inputs");
    }
    return requiredCashCollateral.subtract(confirmedCashCollateral).max(BigDecimal.ZERO);
  }
}
