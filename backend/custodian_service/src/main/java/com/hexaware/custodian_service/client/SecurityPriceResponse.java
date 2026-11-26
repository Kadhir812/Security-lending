package com.hexaware.custodian_service.client;

import java.math.BigDecimal;
import java.time.Instant;

public record SecurityPriceResponse(
    String priceReference,
    String isin,
    String currency,
    BigDecimal marketPrice,
    Instant priceTimestamp,
    String sourceName,
    boolean stale) {}
