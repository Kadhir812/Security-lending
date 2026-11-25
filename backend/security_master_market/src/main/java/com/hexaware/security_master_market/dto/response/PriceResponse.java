package com.hexaware.security_master_market.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceResponse {
  private String priceReference;
  private String isin;
  private String currency;
  private BigDecimal marketPrice;
  private Instant priceTimestamp;
  private String sourceName;
  private boolean stale;
}
