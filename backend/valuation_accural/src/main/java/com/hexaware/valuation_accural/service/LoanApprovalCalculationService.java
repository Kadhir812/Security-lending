package com.hexaware.valuation_accural.service;

import com.hexaware.valuation_accural.client.MarketDataClient;
import com.hexaware.valuation_accural.calculator.LoanApprovalCalculator;
import com.hexaware.valuation_accural.config.LoanCalculationProperties;
import com.hexaware.valuation_accural.dto.request.LoanApprovalCalculationRequest;
import com.hexaware.valuation_accural.dto.response.LoanApprovalCalculationResponse;
import com.hexaware.valuation_accural.exception.CalculationException;
import com.hexaware.valuation_accural.security.authorization.CalculationAuthorization;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanApprovalCalculationService {
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
  private final CalculationAuthorization authorization;
  private final MarketDataClient marketData;
  private final LoanCalculationProperties properties;
  private final Clock clock;
  private final LoanApprovalCalculator calculator;

  public LoanApprovalCalculationResponse calculate(
      Authentication authentication, LoanApprovalCalculationRequest request) {
    authorization.requireAuthoritative(authentication, request.getCustodianOrganizationReference());
    if (request.getQuantity() == null || request.getQuantity() <= 0
        || request.getCoveragePercentage() == null
        || request.getCoveragePercentage().compareTo(ONE_HUNDRED) < 0
        || request.getCurrency() == null
        || !properties.getSupportedCurrencies().contains(request.getCurrency())) {
      throw invalid("Unsupported or invalid calculation inputs");
    }

    var security = marketData.security(request.getSecurityIsin());
    if (!request.getSecurityIsin().equals(security.getIsin())
        || !"ACTIVE".equals(security.getStatus())
        || !request.getCurrency().equals(security.getCurrency())) {
      throw invalid("Security status or currency is not eligible");
    }
    if (!"EQUITY".equals(security.getSecurityType())
        && !"ETF".equals(security.getSecurityType())) {
      throw invalid("Price quote convention is unsupported for this security type");
    }

    var price = marketData.latestPrice(request.getSecurityIsin());
    var now = clock.instant();
    if (!request.getSecurityIsin().equals(price.getIsin())
        || !request.getCurrency().equals(price.getCurrency())
        || price.getMarketPrice() == null
        || price.getMarketPrice().signum() <= 0
        || price.getPriceReference() == null
        || price.getPriceReference().isBlank()
        || price.getSourceName() == null
        || price.getSourceName().isBlank()
        || price.getPriceTimestamp() == null
        || price.getPriceTimestamp().isAfter(now)
        || Duration.between(price.getPriceTimestamp(), now).compareTo(properties.getMaxPriceAge()) > 0
        || price.isStale()) {
      throw invalid("Market price is missing, mismatched, future-dated, or stale");
    }

    var amounts =
        calculator.calculate(
            price.getMarketPrice(), request.getQuantity(), request.getCoveragePercentage());
    BigDecimal loanValue = amounts.loanMarketValue();
    BigDecimal requiredCollateral = amounts.requiredCashCollateral();
    if (loanValue.signum() <= 0 || requiredCollateral.signum() <= 0) {
      throw invalid("Calculated loan or collateral value is not positive");
    }
    return LoanApprovalCalculationResponse.builder()
        .calculationReference(UUID.randomUUID().toString())
        .securityIsin(request.getSecurityIsin())
        .quantity(request.getQuantity())
        .marketPrice(price.getMarketPrice())
        .currency(request.getCurrency())
        .priceReference(price.getPriceReference())
        .priceSource(price.getSourceName())
        .priceTimestamp(price.getPriceTimestamp())
        .calculatedAt(now)
        .loanMarketValue(loanValue)
        .requiredCashCollateral(requiredCollateral)
        .coveragePercentage(request.getCoveragePercentage())
        .cashHaircutPercentage(BigDecimal.ZERO.setScale(4))
        .creditReservationAmount(loanValue)
        .build();
  }

  private CalculationException invalid(String message) {
    return new CalculationException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }
}
