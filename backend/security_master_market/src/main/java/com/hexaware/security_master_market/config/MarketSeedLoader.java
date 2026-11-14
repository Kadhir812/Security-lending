package com.hexaware.security_master_market.config;

import com.hexaware.security_master_market.dto.request.SeedSecurity;
import com.hexaware.security_master_market.enums.SecurityType;
import com.hexaware.security_master_market.service.MarketInput;
import jakarta.validation.Validator;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
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
    if (entries.length != 100
        || Arrays.stream(entries)
                .filter(e -> e.getSecurity().getSecurityType() == SecurityType.EQUITY)
                .count()
            != 90
        || Arrays.stream(entries)
                .filter(e -> e.getSecurity().getSecurityType() == SecurityType.ETF)
                .count()
            != 10
        || Arrays.stream(entries).map(e -> e.getSecurity().getIsin()).distinct().count() != 100)
      throw new IllegalStateException(
          "Seed must contain exactly 90 unique equities and 10 unique ETFs");
    for (SeedSecurity entry : entries) {
      if (!validator.validate(entry).isEmpty())
        throw new IllegalStateException("Invalid development seed");
      var security = entry.getSecurity();
      var price = entry.getPrice();
      String isin = MarketInput.isin(security.getIsin());
      if (!"INR".equals(security.getCurrency()) || !"DEMO_SIMULATED".equals(price.getSourceName()))
        throw new IllegalStateException("Seed must use simulated INR prices");
      jdbc.update(
          """
          INSERT INTO security_master(version,symbol,security_name,isin,security_type,currency,exchange_name,status)
          VALUES(0,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE security_id=security_id
          """,
          MarketInput.upper(security.getSymbol(), "symbol", 50),
          MarketInput.text(security.getSecurityName(), "securityName", 255),
          isin,
          security.getSecurityType().name(),
          "INR",
          security.getExchangeName(),
          security.getStatus().name());
      jdbc.update(
          """
          INSERT INTO security_price(version,price_reference,security_id,market_price,price_timestamp,source_name)
          SELECT 0,?,security_id,?,?,? FROM security_master WHERE isin=? AND currency='INR'
          ON DUPLICATE KEY UPDATE price_id=price_id
          """,
          UUID.fromString(entry.getPriceReference()).toString(),
          price.getMarketPrice().setScale(6, RoundingMode.UNNECESSARY),
          LocalDateTime.ofInstant(MarketInput.timestamp(price.getPriceTimestamp()), ZoneOffset.UTC),
          price.getSourceName(),
          isin);
    }
  }
}
