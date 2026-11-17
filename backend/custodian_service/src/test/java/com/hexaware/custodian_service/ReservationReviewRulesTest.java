package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.service.inventory.InventoryQuantityRules;
import com.hexaware.custodian_service.service.lending.ReservationReviewRules;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReservationReviewRulesTest {
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  @Test
  void usesTheStartOfTheFollowingIndianDayAsReviewDeadline() {
    assertThat(ReservationReviewRules.reviewDeadline(LocalDate.of(2026, 11, 10), IST))
        .isEqualTo(Instant.parse("2026-11-10T18:30:00Z"));
  }

  @Test
  void rejectsApprovalAfterTheReviewDeadlineWithoutImplyingRelease() {
    assertThatThrownBy(
            () ->
                ReservationReviewRules.requireNotOverdue(
                    LocalDate.of(2026, 11, 10),
                    Instant.parse("2026-11-10T18:30:00Z"),
                    IST))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("review deadline");
  }

  @Test
  void overdueReviewDeadlineDoesNotRemoveActiveReservationFromAvailability() {
    assertThat(InventoryQuantityRules.available(100, 0, 25)).isEqualTo(75);
  }
}
