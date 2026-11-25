package com.hexaware.valuation_accural;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.valuation_accural.calculator.DailyLendingFeeCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DailyLendingFeeCalculatorTest {
  private final DailyLendingFeeCalculator calculator = new DailyLendingFeeCalculator();

  @Test
  void roundsDailyAmountHalfUpToCentsWithoutIntermediateRounding() {
    assertThat(calculator.calculate(new BigDecimal("100"), 1, new BigDecimal("1.825")))
        .isEqualByComparingTo("0.01");
    assertThat(calculator.calculate(new BigDecimal("100"), 1, new BigDecimal("1.824635")))
        .isEqualByComparingTo("0.00");
  }
}
