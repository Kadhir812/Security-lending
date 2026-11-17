package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.exception.SetupException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class ReservationReviewRules {
  private ReservationReviewRules() {}

  public static Instant reviewDeadline(LocalDate settlementDate, ZoneId businessZone) {
    return settlementDate.plusDays(1).atStartOfDay(businessZone).toInstant();
  }

  public static void requireNotOverdue(
      LocalDate settlementDate, Instant now, ZoneId businessZone) {
    if (!reviewDeadline(settlementDate, businessZone).isAfter(now)) {
      throw SetupException.conflict("Reservation review deadline has passed");
    }
  }
}
