package com.hexaware.custodian_service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FoundationConfiguration {
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
