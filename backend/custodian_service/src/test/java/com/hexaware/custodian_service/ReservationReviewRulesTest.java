package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.service.inventory.InventoryQuantityRules;
import com.hexaware.custodian_service.service.lending.ReservationReviewRules;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReservationReviewRulesTest {
  @Test
  void usesTheStartOfTheFollowingUtcDayAsReviewDeadline() {
    assertThat(ReservationReviewRules.reviewDeadline(LocalDate.of(2026, 11, 10)))
        .isEqualTo(Instant.parse("2026-11-11T00:00:00Z"));
  }

  @Test
  void rejectsApprovalAfterTheReviewDeadlineWithoutImplyingRelease() {
    assertThatThrownBy(
            () ->
                ReservationReviewRules.requireNotOverdue(
                    LocalDate.of(2026, 11, 10), Instant.parse("2026-11-11T00:00:00Z")))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("review deadline");
  }

  @Test
  void overdueReviewDeadlineDoesNotRemoveActiveReservationFromAvailability() {
    assertThat(InventoryQuantityRules.available(100, 0, 25)).isEqualTo(75);
  }
}
