package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexaware.custodian_service.client.ValuationCalculationResponse;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.service.lending.LoanValuationRules;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LoanValuationRulesTest {
  private static final Instant NOW = Instant.parse("2026-11-08T05:30:00Z");
  private static final LoanRequest REQUEST =
      LoanRequest.builder()
          .requestReference("LR-2026-000042")
          .securityIsin("INE002A01018")
          .quantity(3L)
          .settlementCurrency("INR")
          .requestedSettlementDate(LocalDate.of(2026, 11, 10))
          .build();

  @Test
  void acceptsCalculatedValuesUsingApprovedRoundingAndFreshPrice() {
    assertThatCode(
            () ->
                LoanValuationRules.validate(
                    REQUEST,
                    "INR",
                    new BigDecimal("125"),
                    result(new BigDecimal("300.02"), new BigDecimal("375.03")),
                    NOW,
                    Duration.ofHours(24)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsIncorrectCalculationRounding() {
    assertThatThrownBy(
            () ->
                LoanValuationRules.validate(
                    REQUEST,
                    "INR",
                    new BigDecimal("125"),
                    result(new BigDecimal("300.01"), new BigDecimal("375.02")),
                    NOW,
                    Duration.ofHours(24)))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("rounding");
  }

  private ValuationCalculationResponse result(BigDecimal loanValue, BigDecimal collateral) {
    return ValuationCalculationResponse.builder()
        .calculationReference("e2d92825-60a7-4a54-b9b4-df9f0c77ec71")
        .securityIsin(REQUEST.getSecurityIsin())
        .quantity(REQUEST.getQuantity())
        .marketPrice(new BigDecimal("100.005"))
        .currency("INR")
        .priceReference("f8143470-13ee-4b24-93df-53074689ad26")
        .priceSource("TEST")
        .priceTimestamp(NOW.minusSeconds(30))
        .calculatedAt(NOW.minusSeconds(10))
        .loanMarketValue(loanValue)
        .requiredCashCollateral(collateral)
        .coveragePercentage(new BigDecimal("125"))
        .cashHaircutPercentage(BigDecimal.ZERO)
        .creditReservationAmount(loanValue)
        .build();
  }
}
