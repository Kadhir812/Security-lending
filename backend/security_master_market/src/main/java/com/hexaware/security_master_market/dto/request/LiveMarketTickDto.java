package com.hexaware.security_master_market.dto.request;

import java.math.BigDecimal;
import java.time.Instant;

public record LiveMarketTickDto(
    String securityId,
    String symbol,
    String name,
    String isin,
    String exchange,
    String currency,
    BigDecimal price,
    BigDecimal previousClose,
    BigDecimal openPrice,
    BigDecimal change,
    BigDecimal changePercent,
    BigDecimal dayHigh,
    BigDecimal dayLow,
    BigDecimal bid,
    BigDecimal ask,
    Long volume,
    Instant timestamp,
    String source,
    int hourlyQuotaUsed,
    int hourlyQuotaLimit) {}
