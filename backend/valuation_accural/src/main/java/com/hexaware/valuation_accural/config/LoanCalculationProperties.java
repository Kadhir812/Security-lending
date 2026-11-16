package com.hexaware.valuation_accural.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("app.calculation")
public class LoanCalculationProperties {
  @NotNull private Duration maxPriceAge = Duration.ofHours(24);
  @NotEmpty private List<String> supportedCurrencies = List.of("INR", "USD", "EUR", "GBP");

  @PostConstruct
  void validate() {
    if (maxPriceAge == null || maxPriceAge.isNegative() || maxPriceAge.isZero()) {
      throw new IllegalArgumentException("app.calculation.max-price-age must be positive");
    }
  }
}
