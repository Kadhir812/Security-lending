package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.quickfix.SettlementStatusFixMessageBuilder;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementStatusFixMessageBuilderTest {
  @Test
  void buildsCustomU2ForInitialSecuritiesDelivery() {
    Instant occurredAt = Instant.parse("2026-11-11T12:00:01.123456Z");
    EventEnvelope event = EventEnvelope.builder()
        .eventId("22f9ba67-e3a2-4c49-9e03-b4d72a33d31a")
        .eventType("SETTLEMENT_INSTRUCTION_UPDATED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId("INSTR-001")
        .aggregateReference("INSTR-001")
        .aggregateVersion(1L)
        .occurredAt(occurredAt)
        .senderOrganizationReference("CUSTODIAN-001")
        .receiverOrganizationReference("BORROWER-001")
        .payload(Map.of())
        .build();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("aggregateVersion", 1L);
    payload.put("transactionReference", "TX-001");
    payload.put("instructionReference", "INSTR-001");
    payload.put("instructionType", "SECURITY_DELIVERY");
    payload.put("settlementStatus", "SETTLED");
    payload.put("instructionStatus", "SETTLED");
    payload.put("currency", "INR");
    payload.put("quantity", 100L);
    payload.put("securityIsin", "INE000A01012");
    payload.put("contractStatus", "ON_LOAN");
    payload.put("contractVersion", 3L);
    payload.put("deliveredQuantity", 100L);
    payload.put("outstandingQuantity", 100L);
    payload.put("confirmedCashCollateral", "1050.00");
    payload.put("settledAt", "2026-11-11T12:00:00.123456Z");
    payload.put("externalConfirmationReference", "CONFIRM-001");
    payload.put("simulatedConfirmation", true);

    String wireMessage = new SettlementStatusFixMessageBuilder().build(event, payload);

    assertThat(wireMessage)
        .contains("35=U2")
        .contains("22=4")
        .contains("9154=100")
        .contains("9141=INSTR-001")
        .contains("9151=22f9ba67-e3a2-4c49-9e03-b4d72a33d31a")
        .contains("9153=2026-11-11T12:00:00.123456Z");
  }
}
