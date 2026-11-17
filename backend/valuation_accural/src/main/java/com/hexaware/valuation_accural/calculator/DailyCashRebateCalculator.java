package com.hexaware.valuation_accural.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class DailyCashRebateCalculator {
  private static final BigDecimal ANNUAL_PERCENT_DENOMINATOR = new BigDecimal("36500");

  public BigDecimal calculate(BigDecimal settledCashAmount, BigDecimal annualRebatePercentage) {
    return settledCashAmount
        .multiply(annualRebatePercentage)
        .divide(ANNUAL_PERCENT_DENOMINATOR, 2, RoundingMode.HALF_UP);
  }
}
