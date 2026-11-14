package com.hexaware.valuation_accural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.hexaware.valuation_accural.client.MarketDataClient;
import com.hexaware.valuation_accural.config.LoanCalculationProperties;
import com.hexaware.valuation_accural.dto.request.LoanApprovalCalculationRequest;
import com.hexaware.valuation_accural.exception.CalculationException;
import com.hexaware.valuation_accural.security.authorization.CalculationAuthorization;
import com.hexaware.valuation_accural.service.LoanApprovalCalculationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class LoanApprovalCalculationServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-08T05:30:00Z");
  @Mock CalculationAuthorization authorization;
  @Mock MarketDataClient marketData;
  @Mock Authentication authentication;
  private LoanApprovalCalculationService service;
  private LoanCalculationProperties properties;
  private LoanApprovalCalculationRequest request;
  private MarketDataClient.PriceReference price;

  @BeforeEach
  void setUp() {
    properties = new LoanCalculationProperties();
    properties.setMaxPriceAge(Duration.ofHours(24));
    service =
        new LoanApprovalCalculationService(
            authorization,
            marketData,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new com.hexaware.valuation_accural.calculator.LoanApprovalCalculator());
    request =
        LoanApprovalCalculationRequest.builder()
            .securityIsin("INE002A01018")
            .quantity(3L)
            .coveragePercentage(new BigDecimal("125"))
            .currency("INR")
            .custodianOrganizationReference("CUSTODIAN-001")
            .build();
    var security = new MarketDataClient.SecurityReference();
    security.setIsin(request.getSecurityIsin());
    security.setSecurityType("EQUITY");
    security.setCurrency("INR");
    security.setStatus("ACTIVE");
    lenient().when(marketData.security(request.getSecurityIsin())).thenReturn(security);

    price = new MarketDataClient.PriceReference();
    price.setPriceReference("e2d92825-60a7-4a54-b9b4-df9f0c77ec71");
    price.setIsin(request.getSecurityIsin());
    price.setCurrency("INR");
    price.setMarketPrice(new BigDecimal("100.005"));
    price.setPriceTimestamp(NOW.minusSeconds(30));
    price.setSourceName("TEST");
    lenient().when(marketData.latestPrice(request.getSecurityIsin())).thenReturn(price);
  }

  @Test
  void appliesApprovedHalfUpAndCeilingRounding() {
    var result = service.calculate(authentication, request);

    assertThat(result.getLoanMarketValue()).isEqualByComparingTo("300.02");
    assertThat(result.getRequiredCashCollateral()).isEqualByComparingTo("375.03");
    assertThat(result.getCreditReservationAmount()).isEqualByComparingTo("300.02");
    assertThat(result.getCashHaircutPercentage()).isEqualByComparingTo("0");
    assertThat(result.getPriceReference()).isEqualTo(price.getPriceReference());
    assertThat(result.getPriceTimestamp()).isEqualTo(price.getPriceTimestamp());
  }

  @Test
  void rejectsStaleMarketPrice() {
    price.setPriceTimestamp(NOW.minus(Duration.ofHours(25)));

    assertThatThrownBy(() -> service.calculate(authentication, request))
        .isInstanceOf(CalculationException.class)
        .hasMessageContaining("stale");
  }

  @Test
  void blocksUnsupportedBondQuoteConvention() {
    var security = new MarketDataClient.SecurityReference();
    security.setIsin(request.getSecurityIsin());
    security.setSecurityType("CORPORATE_BOND");
    security.setCurrency("INR");
    security.setStatus("ACTIVE");
    when(marketData.security(request.getSecurityIsin())).thenReturn(security);

    assertThatThrownBy(() -> service.calculate(authentication, request))
        .isInstanceOf(CalculationException.class)
        .hasMessageContaining("quote convention");
  }
}
