package com.hexaware.security_master_market.mapper;

import com.hexaware.security_master_market.dto.response.PriceResponse;
import com.hexaware.security_master_market.dto.response.SecurityResponse;
import com.hexaware.security_master_market.entity.SecurityMaster;
import com.hexaware.security_master_market.entity.SecurityPrice;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SecurityMapper {
  public SecurityResponse security(SecurityMaster s) {
    return SecurityResponse.builder()
        .securityId(s.getSecurityId())
        .version(s.getVersion())
        .isin(s.getIsin())
        .symbol(s.getSymbol())
        .securityName(s.getSecurityName())
        .securityType(s.getSecurityType())
        .currency(s.getCurrency())
        .exchangeName(s.getExchangeName())
        .status(s.getStatus())
        .build();
  }

  public PriceResponse price(SecurityMaster s, SecurityPrice p, Instant at, Duration maxAge) {
    return PriceResponse.builder()
        .priceReference(p.getPriceReference())
        .isin(s.getIsin())
        .currency(s.getCurrency())
        .marketPrice(p.getMarketPrice())
        .priceTimestamp(p.getPriceTimestamp())
        .sourceName(p.getSourceName())
        .stale(Duration.between(p.getPriceTimestamp(), at).compareTo(maxAge) > 0)
        .build();
  }
}
