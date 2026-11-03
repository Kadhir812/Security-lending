package com.hexaware.security_master_market.config;

import com.hexaware.security_master_market.service.MarketInput;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.market.prices")
@Data
public class PricePolicy {
  private List<String> sourcePriority = List.of("DEMO_SIMULATED");
  private Duration maxAge = Duration.ofHours(24);

  @PostConstruct
  void validate() {
    if (sourcePriority == null
        || sourcePriority.isEmpty()
        || sourcePriority.size() > 20
        || maxAge == null
        || maxAge.isNegative()
        || maxAge.isZero())
      throw new IllegalStateException("Configure 1-20 price sources and a positive max-age");
    sourcePriority = sourcePriority.stream().map(s -> MarketInput.upper(s, "source", 100)).toList();
    if (sourcePriority.stream().distinct().count() != sourcePriority.size())
      throw new IllegalStateException("Duplicate price source priorities");
  }
}
