package com.hexaware.security_master_market.service;

import com.hexaware.security_master_market.config.PricePolicy;
import com.hexaware.security_master_market.dto.request.SubmitPriceRequest;
import com.hexaware.security_master_market.dto.response.PageResponse;
import com.hexaware.security_master_market.dto.response.PriceResponse;
import com.hexaware.security_master_market.entity.SecurityMaster;
import com.hexaware.security_master_market.entity.SecurityPrice;
import com.hexaware.security_master_market.exception.MarketDataException;
import com.hexaware.security_master_market.mapper.SecurityMapper;
import com.hexaware.security_master_market.repository.SecurityPriceRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SecurityPriceService {
  private final SecurityMasterService securities;
  private final SecurityPriceRepository prices;
  private final JdbcTemplate jdbc;
  private final SecurityMapper mapper;
  private final PricePolicy policy;
  private final Clock clock;

  @Transactional
  public PriceResponse submit(String isin, SubmitPriceRequest request) {
    SecurityMaster security = securities.lock(isin);
    Instant timestamp = MarketInput.timestamp(request.getPriceTimestamp());
    String source = MarketInput.upper(request.getSourceName(), "sourceName", 100);
    BigDecimal price = request.getMarketPrice();
    if (price == null
        || price.signum() <= 0
        || price.scale() > 6
        || price.precision() - price.scale() > 14)
      throw MarketDataException.invalid("Price must be positive and fit DECIMAL(20,6)");
    price = price.setScale(6, RoundingMode.UNNECESSARY);
    // The unique key arbitrates concurrent submissions. The duplicate branch changes no data.
    jdbc.update(
        """
        INSERT INTO security_price(version,price_reference,security_id,market_price,price_timestamp,source_name)
        VALUES(0,?,?,?,?,?) ON DUPLICATE KEY UPDATE price_id=price_id
        """,
        UUID.randomUUID().toString(),
        security.getSecurityId(),
        price,
        LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC),
        source);
    // A locking read sees the committed winner even under MySQL REPEATABLE READ.
    SecurityPrice observation =
        prices
            .findBySecurityIdAndPriceTimestampAndSourceName(
                security.getSecurityId(), timestamp, source)
            .orElseThrow(
                () -> MarketDataException.conflict("Observation reference collision; resubmit"));
    if (observation.getMarketPrice().compareTo(price) != 0)
      throw MarketDataException.conflict(
          "A different price already exists for this security, timestamp and source");
    return mapper.price(security, observation, clock.instant(), policy.getMaxAge());
  }

  @Transactional(readOnly = true)
  public PriceResponse latest(String isin, String source) {
    return select(isin, clock.instant().truncatedTo(ChronoUnit.MICROS), source);
  }

  @Transactional(readOnly = true)
  public PriceResponse asOf(String isin, Instant at, String source) {
    return select(isin, MarketInput.timestamp(at), source);
  }

  private PriceResponse select(String isin, Instant at, String source) {
    SecurityMaster security = securities.require(isin);
    List<String> sources =
        source == null
            ? policy.getSourcePriority()
            : List.of(MarketInput.upper(source, "source", 100));
    // First configured source with an observation wins, then newest observation at/before cutoff.
    for (String candidate : sources) {
      var observation =
          prices
              .findFirstBySecurityIdAndSourceNameAndPriceTimestampLessThanEqualOrderByPriceTimestampDesc(
                  security.getSecurityId(), candidate, at);
      if (observation.isPresent())
        return mapper.price(security, observation.get(), at, policy.getMaxAge());
    }
    throw MarketDataException.missing(
        "No applicable price for the requested time and source policy");
  }

  @Transactional(readOnly = true)
  public PageResponse<PriceResponse> history(
      String isin, Instant from, Instant to, String source, int page, int size) {
    SecurityMaster security = securities.require(isin);
    if (from != null) MarketInput.timestamp(from);
    if (to != null) MarketInput.timestamp(to);
    if (from != null && to != null && from.isAfter(to))
      throw MarketDataException.invalid("from must be at or before to");
    String selectedSource = source == null ? null : MarketInput.upper(source, "source", 100);
    Instant now = clock.instant();
    Specification<SecurityPrice> specification =
        (root, query, cb) -> {
          var filters = new ArrayList<Predicate>();
          filters.add(cb.equal(root.get("securityId"), security.getSecurityId()));
          if (from != null) filters.add(cb.greaterThanOrEqualTo(root.get("priceTimestamp"), from));
          if (to != null) filters.add(cb.lessThanOrEqualTo(root.get("priceTimestamp"), to));
          if (selectedSource != null) filters.add(cb.equal(root.get("sourceName"), selectedSource));
          return cb.and(filters.toArray(Predicate[]::new));
        };
    return PageResponse.from(
        prices
            .findAll(
                specification,
                MarketInput.page(
                    page, size, Sort.by(Sort.Direction.DESC, "priceTimestamp", "priceId")))
            .map(p -> mapper.price(security, p, now, policy.getMaxAge())));
  }
}
