package com.hexaware.valuation_accural.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.hexaware.valuation_accural.calculator.DailyLendingFeeCalculator;
import com.hexaware.valuation_accural.client.MarketDataClient;
import com.hexaware.valuation_accural.config.LoanCalculationProperties;
import com.hexaware.valuation_accural.dto.request.LendingFeeCalculationRequest;
import com.hexaware.valuation_accural.exception.CalculationException;
import com.hexaware.valuation_accural.repository.CalculationResultRepository;
import com.hexaware.valuation_accural.repository.CalculationRunRepository;
import com.hexaware.valuation_accural.security.authorization.CalculationAuthorization;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LendingFeeCalculationTimeTest {
  private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
  private LendingFeeCalculationService service;

  @BeforeEach
  void setUp() {
    service =
        new LendingFeeCalculationService(
            mock(CalculationAuthorization.class),
            mock(MarketDataClient.class),
            new LoanCalculationProperties(),
            new DailyLendingFeeCalculator(),
            Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")),
            mock(CalculationRunRepository.class),
            mock(CalculationResultRepository.class),
            new ObjectMapper(),
            mock(org.springframework.transaction.PlatformTransactionManager.class));
  }

  @Test
  void acceptsIstMidnightAndRejectsUtcMidnightAsTheDailyFeeCutoff() {
    LendingFeeCalculationRequest request =
        request(Instant.parse("2026-11-17T18:30:00Z"));
    assertThatCode(() -> service.validate(request)).doesNotThrowAnyException();

    request.setCutoff(Instant.parse("2026-11-18T00:00:00Z"));
    assertThatThrownBy(() -> service.validate(request))
        .isInstanceOf(CalculationException.class)
        .hasMessageContaining("IST cutoff");
  }

  private LendingFeeCalculationRequest request(Instant cutoff) {
    return LendingFeeCalculationRequest.builder()
        .calculationRequestReference("CALC-001")
        .transactionReference("TX-001")
        .securityIsin("INE002A01018")
        .accrualDate(LocalDate.parse("2026-11-17"))
        .cutoff(cutoff)
        .settledOutstandingQuantity(100L)
        .annualFeePercentage(new BigDecimal("1.0"))
        .currency("INR")
        .dayCountBasis("ACT_365")
        .inputVersion("VERSION-1")
        .borrowerOrganizationReference("BORROWER-001")
        .custodianOrganizationReference("CUSTODIAN-001")
        .build();
  }
}
