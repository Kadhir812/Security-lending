package com.hexaware.security_master_market.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FoundationConfiguration {
  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Kolkata"));
  }
}
