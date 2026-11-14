package com.hexaware.custodian_service.service.fees;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.custodian_service.entity.SettlementInstruction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LendingFeeAccrualHistoryTest {
  private final LendingFeeAccrualHistory history =
      new LendingFeeAccrualHistory(null, null, null);

  @Test
  void includesDeliveryAndReturnSettlementDatesInDailyQuantity() {
    List<SettlementInstruction> settlements =
        List.of(
            settlement("SECURITY_DELIVERY", 100, "2026-11-01T10:00:00Z"),
            settlement("SECURITY_RETURN", 40, "2026-11-02T18:00:00Z"));

    assertThat(history.accrualQuantityOnDate(settlements, LocalDate.parse("2026-11-01")))
        .isEqualTo(100);
    assertThat(history.accrualQuantityOnDate(settlements, LocalDate.parse("2026-11-02")))
        .isEqualTo(100);
    assertThat(history.accrualQuantityOnDate(settlements, LocalDate.parse("2026-11-03")))
        .isEqualTo(60);
  }

  private SettlementInstruction settlement(String type, long quantity, String settledAt) {
    return SettlementInstruction.builder()
        .instructionType(type)
        .quantity(BigDecimal.valueOf(quantity))
        .settledAt(Instant.parse(settledAt))
        .build();
  }
}
