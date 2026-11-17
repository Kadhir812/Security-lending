package com.hexaware.valuation_accural;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.valuation_accural.calculator.DailyCashRebateCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DailyCashRebateCalculatorTest {
  private final DailyCashRebateCalculator calculator = new DailyCashRebateCalculator();

  @Test
  void calculatesOneDayOfRebateOnSettledCashAndRoundsHalfUp() {
    assertThat(calculator.calculate(new BigDecimal("105000"), new BigDecimal("3")))
        .isEqualByComparingTo("8.63");
    assertThat(calculator.calculate(new BigDecimal("100"), new BigDecimal("1.825")))
        .isEqualByComparingTo("0.01");
  }
}
