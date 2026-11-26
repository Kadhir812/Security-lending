package com.hexaware.valuation_accural.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class DailyCashCollateralRebateCalculator {
  private static final BigDecimal ANNUAL_PERCENT_AND_ACT_365 = new BigDecimal("36500");

  public BigDecimal calculate(BigDecimal settledCashCollateral, BigDecimal annualRebatePercentage) {
    return settledCashCollateral
        .multiply(annualRebatePercentage)
        .divide(ANNUAL_PERCENT_AND_ACT_365, 2, RoundingMode.HALF_UP);
  }
}
