package com.hexaware.security_master_market.config;

import com.hexaware.security_master_market.dto.request.SeedSecurity;
import com.hexaware.security_master_market.enums.SecurityType;
import com.hexaware.security_master_market.service.MarketInput;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Explicitly enabled development data; never replaces user-maintained rows. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.market.seed.enabled", havingValue = "true")
public class MarketSeedLoader implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final Validator validator;

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) throws Exception {
    SeedSecurity[] entries;
    try (var input = new ClassPathResource("seed/indian-securities.json").getInputStream()) {
      entries = mapper.readValue(input, SeedSecurity[].class);
    }
    Set<String> symbols =
        java.util.Arrays.stream(entries)
            .map(entry -> entry.getSecurity().getSymbol())
            .collect(Collectors.toSet());
    if (entries.length != 2
        || !symbols.equals(Set.of("INFY", "TCS"))
        || java.util.Arrays.stream(entries)
                .anyMatch(entry -> entry.getSecurity().getSecurityType() != SecurityType.EQUITY)
        || java.util.Arrays.stream(entries)
                .map(entry -> entry.getSecurity().getIsin())
                .distinct()
                .count()
            != 2)
      throw new IllegalStateException("Seed must contain exactly INFY and TCS as unique equities");
    for (SeedSecurity entry : entries) {
      if (!validator.validate(entry).isEmpty())
        throw new IllegalStateException("Invalid development seed");
      var security = entry.getSecurity();
      String isin = MarketInput.isin(security.getIsin());
      if (!"INR".equals(security.getCurrency()))
        throw new IllegalStateException("Seed securities must use INR");
      jdbc.update(
          """
          INSERT INTO security_master(version,symbol,security_name,isin,security_type,currency,exchange_name,status,sedol,sector,lot_size)
          VALUES(0,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE
            sedol=COALESCE(sedol,VALUES(sedol)),
            sector=COALESCE(sector,VALUES(sector)),
            lot_size=COALESCE(lot_size,VALUES(lot_size))
          """,
          MarketInput.upper(security.getSymbol(), "symbol", 50),
          MarketInput.text(security.getSecurityName(), "securityName", 255),
          isin,
          security.getSecurityType().name(),
          "INR",
          security.getExchangeName(),
          security.getStatus().name(),
          MarketInput.optional(security.getSedol(), "sedol", 7),
          MarketInput.optional(security.getSector(), "sector", 100),
          security.getLotSize());
    }
  }
}
