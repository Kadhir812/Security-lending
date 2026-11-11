package com.hexaware.valuation_accural.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("app.security-market")
public class MarketDataProperties {
  @NotBlank private String baseUrl;
}
