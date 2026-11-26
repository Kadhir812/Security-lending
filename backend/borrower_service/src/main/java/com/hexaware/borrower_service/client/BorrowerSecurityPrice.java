package com.hexaware.borrower_service.client;

import java.math.BigDecimal;
import java.time.Instant;

public record BorrowerSecurityPrice(
    String priceReference,
    String isin,
    String currency,
    BigDecimal marketPrice,
    Instant priceTimestamp,
    String sourceName,
    boolean stale) {}
