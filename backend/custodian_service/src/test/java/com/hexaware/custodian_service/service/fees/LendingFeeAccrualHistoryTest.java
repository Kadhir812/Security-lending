package com.hexaware.custodian_service.service.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LendingFeeAccrualHistoryTest {
  private static final String TRANSACTION_REFERENCE = "CTR-TEST";
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

  @Test
  void readsNestedOutboxPayloadAndIgnoresUnlinkedDuplicateAccrualEvents() {
    OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    List<OutboxEvent> events =
        List.of(
            chargeEvent(
                "first-13", 1, "2026-11-13", "result-13-a", null, "2026-11-15T12:00:00Z"),
            chargeEvent(
                "first-14", 1, "2026-11-14", "result-14-a", null, "2026-11-15T12:00:01Z"),
            chargeEvent(
                "duplicate-13",
                1,
                "2026-11-13",
                "result-13-duplicate",
                null,
                "2026-11-15T12:00:02Z"),
            chargeEvent(
                "correction-13",
                2,
                "2026-11-13",
                "result-13-b",
                "result-13-a",
                "2026-11-15T12:00:03Z"),
            chargeEvent(
                "replay-14",
                2,
                "2026-11-14",
                "result-14-a",
                null,
                "2026-11-15T12:00:04Z"));
    when(outbox
            .findByEventTypeAndAggregateTypeAndAggregateReferenceOrderByAggregateVersionAscCreatedAtAsc(
                "LENDING_FEE_CHARGE_UPDATED", "LENDING_FEE_CHARGE", TRANSACTION_REFERENCE))
        .thenReturn(events);
    when(outbox.findMaxAggregateVersion(
            "LENDING_FEE_CHARGE_UPDATED", "LENDING_FEE_CHARGE", TRANSACTION_REFERENCE))
        .thenReturn(2L);
    LendingFeeAccrualHistory accrualHistory =
        new LendingFeeAccrualHistory(null, null, outbox);

    assertThat(accrualHistory.acceptedChargeEvents(TRANSACTION_REFERENCE))
        .extracting(event -> accrualHistory.businessPayload(event).get("resultReference"))
        .containsExactly("result-13-a", "result-14-a", "result-13-b");
    assertThat(
            accrualHistory.eventsForDay(
                TRANSACTION_REFERENCE, LocalDate.parse("2026-11-13")))
        .extracting(event -> accrualHistory.businessPayload(event).get("resultReference"))
        .containsExactly("result-13-a", "result-13-b");
    assertThat(accrualHistory.nextAggregateVersion(TRANSACTION_REFERENCE)).isEqualTo(3);
  }

  private OutboxEvent chargeEvent(
      String eventId,
      long aggregateVersion,
      String accrualDate,
      String resultReference,
      String supersedesResultReference,
      String createdAt) {
    Map<String, Object> businessPayload = new LinkedHashMap<>();
    businessPayload.put("transactionReference", TRANSACTION_REFERENCE);
    businessPayload.put("itemKey", "LENDING_FEE:" + accrualDate);
    businessPayload.put("accrualDate", accrualDate);
    businessPayload.put("resultReference", resultReference);
    businessPayload.put("supersedesResultReference", supersedesResultReference);
    return OutboxEvent.builder()
        .eventId(eventId)
        .eventType("LENDING_FEE_CHARGE_UPDATED")
        .aggregateType("LENDING_FEE_CHARGE")
        .aggregateReference(TRANSACTION_REFERENCE)
        .aggregateVersion(aggregateVersion)
        .createdAt(Instant.parse(createdAt))
        .payload(Map.of("aggregateVersion", aggregateVersion, "payload", businessPayload))
        .build();
  }

  private SettlementInstruction settlement(String type, long quantity, String settledAt) {
    return SettlementInstruction.builder()
        .instructionType(type)
        .quantity(BigDecimal.valueOf(quantity))
        .settledAt(Instant.parse(settledAt))
        .build();
  }
}
