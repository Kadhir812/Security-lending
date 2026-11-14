package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import quickfix.Message;

class SettlementStatusFixMessageParserTest {
  @Test
  void parsesAndCorrelatesInitialSettlementU2() {
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
    Message message = new Message();
    var header = message.getHeader();
    header.setString(8, "FIX.4.4");
    header.setString(35, "U2");
    header.setString(49, "CUSTODIAN-001");
    header.setString(56, "BORROWER-001");
    header.setInt(34, 2);
    header.setString(52, "20261111-12:00:01.123");
    message.setString(15, "INR");
    message.setString(22, "4");
    message.setString(48, "INE000A01012");
    message.setString(60, "20261111-12:00:00.123");
    message.setString(9140, "TX-001");
    message.setString(9141, "INSTR-001");
    message.setString(9142, "SECURITY_DELIVERY");
    message.setString(9143, "SETTLED");
    message.setString(9144, "ON_LOAN");
    message.setString(9145, "3");
    message.setString(9146, "100");
    message.setString(9147, "100");
    message.setString(9148, "1050.00");
    message.setString(9149, "CONFIRM-001");
    message.setString(9150, "Y");
    message.setString(9151, "22f9ba67-e3a2-4c49-9e03-b4d72a33d31a");
    message.setString(9152, "1");
    message.setString(9153, "2026-11-11T12:00:00.123456Z");
    message.setString(9154, "100");

    Map<String, Object> payload =
        new LoanRequestFixMessageBuilder().parseSettlementStatusAndValidate(event, message.toString());

    assertThat(payload)
        .containsEntry("transactionReference", "TX-001")
        .containsEntry("instructionReference", "INSTR-001")
        .containsEntry("quantity", 100L)
        .containsEntry("settlementStatus", "SETTLED")
        .containsEntry("settledAt", "2026-11-11T12:00:00.123456Z");
  }
}
