package com.hexaware.custodian_service.service.agreement;

import java.time.Instant;
import java.time.LocalDate;

public final class EffectiveDateRanges {

  private EffectiveDateRanges() {}

  public static boolean overlapsInclusive(
      LocalDate firstFrom, LocalDate firstTo, LocalDate secondFrom, LocalDate secondTo) {
    return (firstTo == null || !firstTo.isBefore(secondFrom))
        && (secondTo == null || !secondTo.isBefore(firstFrom));
  }

  public static boolean overlapsHalfOpen(
      Instant firstFrom, Instant firstTo, Instant secondFrom, Instant secondTo) {
    return (firstTo == null || firstTo.isAfter(secondFrom))
        && (secondTo == null || secondTo.isAfter(firstFrom));
  }
}
