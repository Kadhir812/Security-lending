package com.hexaware.security_master_market.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Advances opted-in demo quotes as new simulated observations. It never modifies
 * historical prices or refreshes observations from a real market-data source.
 */
@Component
@RequiredArgsConstructor
@Order(2)
public class SimulatedPriceRefresh implements ApplicationRunner {
  private static final String SIMULATED_SOURCE = "DEMO_SIMULATED";
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final PricePolicy policy;

  @Override
  public void run(ApplicationArguments args) {
    refreshStaleSimulatedQuotes();
  }

  @Scheduled(fixedDelayString = "#{@pricePolicy.simulatedRefreshInterval.toMillis()}")
  public void refreshStaleSimulatedQuotes() {
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Instant refreshBefore = now.minus(policy.getMaxAge().dividedBy(2));
    List<SimulatedQuote> staleQuotes =
        jdbc.query(
            """
            SELECT p.security_id, p.market_price
            FROM security_price p
            WHERE p.source_name = ?
              AND p.price_timestamp < ?
              AND NOT EXISTS (
                SELECT 1
                FROM security_price newer
                WHERE newer.security_id = p.security_id
                  AND newer.source_name = p.source_name
                  AND newer.price_timestamp > p.price_timestamp
              )
            ORDER BY p.security_id
            """,
            (row, index) -> new SimulatedQuote(row.getLong("security_id"), row.getBigDecimal("market_price")),
            SIMULATED_SOURCE,
            LocalDateTime.ofInstant(refreshBefore, ZoneOffset.UTC));

    for (SimulatedQuote quote : staleQuotes) {
      jdbc.update(
          """
          INSERT INTO security_price(version,price_reference,security_id,market_price,price_timestamp,source_name)
          VALUES(0,?,?,?,?,?)
          ON DUPLICATE KEY UPDATE price_id=price_id
          """,
          UUID.randomUUID().toString(),
          quote.securityId(),
          quote.marketPrice(),
          LocalDateTime.ofInstant(now, ZoneOffset.UTC),
          SIMULATED_SOURCE);
    }
  }

  private record SimulatedQuote(long securityId, BigDecimal marketPrice) {}
}
