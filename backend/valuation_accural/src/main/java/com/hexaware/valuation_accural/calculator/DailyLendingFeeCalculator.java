package com.hexaware.valuation_accural.calculator;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.stereotype.Component;

@Component
public class DailyLendingFeeCalculator {
  private static final BigDecimal ANNUAL_PERCENT_DENOMINATOR = new BigDecimal("36500");

  public BigDecimal calculate(
      BigDecimal price, long settledQuantity, BigDecimal annualFeePercentage) {
    BigDecimal centsNumerator = price.multiply(BigDecimal.valueOf(settledQuantity))
        .multiply(annualFeePercentage)
        .movePointRight(2);
    BigDecimal[] quotientAndRemainder =
        centsNumerator.divideAndRemainder(ANNUAL_PERCENT_DENOMINATOR);
    BigInteger cents = quotientAndRemainder[0].toBigIntegerExact();
    if (quotientAndRemainder[1].multiply(BigDecimal.valueOf(2))
        .compareTo(ANNUAL_PERCENT_DENOMINATOR) >= 0) {
      cents = cents.add(BigInteger.ONE);
    }
    return new BigDecimal(cents, 2);
  }
}
