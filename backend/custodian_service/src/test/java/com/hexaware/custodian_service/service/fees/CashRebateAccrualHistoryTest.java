package com.hexaware.custodian_service.service.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.ContractStatusHistoryRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashRebateAccrualHistoryTest {
  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private final CashRebateAccrualHistory history =
      new CashRebateAccrualHistory(
          mock(SettlementInstructionRepository.class),
          mock(CollateralRepository.class),
          mock(ContractStatusHistoryRepository.class),
          mock(OutboxEventRepository.class),
          Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), IST));

  @Test
  void includesCashTransferAndReleaseSettlementDatesAsFullAccrualDays() {
    List<SettlementInstruction> settlements =
        List.of(
            settlement("COLLATERAL_TRANSFER", "100.00", "2026-11-15T18:45:00Z"),
            settlement("COLLATERAL_RELEASE", "40.00", "2026-11-16T18:45:00Z"));

    assertThat(history.cashBalanceOnDate(settlements, LocalDate.parse("2026-11-16")))
        .isEqualByComparingTo("100.00");
    assertThat(history.cashBalanceOnDate(settlements, LocalDate.parse("2026-11-17")))
        .isEqualByComparingTo("100.00");
    assertThat(history.cashBalanceOnDate(settlements, LocalDate.parse("2026-11-18")))
        .isEqualByComparingTo("60.00");
  }

  private SettlementInstruction settlement(String type, String amount, String settledAt) {
    return SettlementInstruction.builder()
        .instructionType(type)
        .amount(new BigDecimal(amount))
        .settledAt(Instant.parse(settledAt))
        .build();
  }
}
