package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.exception.SetupException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class ReservationReviewRules {
  private ReservationReviewRules() {}

  public static Instant reviewDeadline(LocalDate settlementDate) {
    return settlementDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  public static void requireNotOverdue(LocalDate settlementDate, Instant now) {
    if (!reviewDeadline(settlementDate).isAfter(now)) {
      throw SetupException.conflict("Reservation review deadline has passed");
    }
  }
}
