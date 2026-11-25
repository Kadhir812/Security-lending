package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.client.ValuationCalculationResponse;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.exception.SetupException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class LoanValuationRules {
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private LoanValuationRules() {}

  public static void validate(
      LoanRequest request,
      String requiredCurrency,
      BigDecimal requiredCoverage,
      ValuationCalculationResponse result,
      Instant now,
      Duration maxPriceAge) {
    if (result == null
        || !Objects.equals(request.getSecurityIsin(), result.getSecurityIsin())
        || !Objects.equals(request.getQuantity(), result.getQuantity())
        || !Objects.equals(request.getSettlementCurrency(), result.getCurrency())
        || !Objects.equals(requiredCurrency, result.getCurrency())
        || result.getMarketPrice() == null
        || result.getMarketPrice().signum() <= 0
        || result.getLoanMarketValue() == null
        || result.getRequiredCashCollateral() == null
        || result.getCoveragePercentage() == null
        || requiredCoverage == null
        || result.getCoveragePercentage().compareTo(requiredCoverage) != 0
        || result.getCashHaircutPercentage() == null
        || result.getCashHaircutPercentage().signum() != 0
        || result.getCreditReservationAmount() == null
        || result.getCreditReservationAmount().compareTo(result.getLoanMarketValue()) != 0
        || blank(result.getCalculationReference())
        || blank(result.getPriceReference())
        || blank(result.getPriceSource())
        || result.getPriceTimestamp() == null
        || result.getCalculatedAt() == null
        || result.getCalculatedAt().isAfter(now)
        || result.getPriceTimestamp().isAfter(now)
        || maxPriceAge == null
        || maxPriceAge.isNegative()
        || maxPriceAge.isZero()
        || Duration.between(result.getPriceTimestamp(), now).compareTo(maxPriceAge) > 0) {
      throw SetupException.conflict("Valuation result is incomplete, inconsistent, or stale");
    }

    BigDecimal expectedLoanValue =
        result
            .getMarketPrice()
            .multiply(BigDecimal.valueOf(request.getQuantity()))
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal expectedCollateral =
        expectedLoanValue
            .multiply(requiredCoverage)
            .divide(ONE_HUNDRED, 2, RoundingMode.CEILING);
    if (expectedLoanValue.signum() <= 0
        || expectedCollateral.signum() <= 0
        || result.getLoanMarketValue().compareTo(expectedLoanValue) != 0
        || result.getRequiredCashCollateral().compareTo(expectedCollateral) != 0) {
      throw SetupException.conflict("Valuation result does not follow the approved rounding rules");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
