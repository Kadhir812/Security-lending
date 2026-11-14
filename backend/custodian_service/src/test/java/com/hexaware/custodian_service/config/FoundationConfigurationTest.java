package com.hexaware.custodian_service.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class FoundationConfigurationTest {

  @Test
  void businessDateUsesIndianCalendarAcrossUtcDateBoundary() {
    Clock configuredClock = new FoundationConfiguration().clock();
    Clock clock =
        Clock.fixed(Instant.parse("2026-11-12T20:00:00Z"), configuredClock.getZone());

    assertEquals(ZoneId.of("Asia/Kolkata"), configuredClock.getZone());
    assertEquals(LocalDate.of(2026, 11, 13), LocalDate.now(clock));
  }
}
