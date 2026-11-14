package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.kafka.inbox.SecurityReturnReceivedHandler;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityReturnReceivedHandlerTest {
  private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
  private static final String INSTRUCTION_REFERENCE = "RET-001";
  @Mock private LoanContractViewRepository contracts;
  @Mock private LoanRequestRepository requests;
  private SecurityReturnReceivedHandler handler;
  private LoanContractView contract;

  @BeforeEach
  void setUp() {
    handler = new SecurityReturnReceivedHandler(
        contracts, requests, Clock.fixed(NOW, ZoneOffset.UTC));
    Map<String, Object> instruction = new LinkedHashMap<>();
    instruction.put("instructionReference", INSTRUCTION_REFERENCE);
    instruction.put("quantity", 10L);
    instruction.put("recallReference", null);
    instruction.put("securityIsin", "INE002A01018");
    instruction.put("currency", "INR");
    instruction.put("status", "SUBMITTED");
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("returnInstructions", new ArrayList<>(List.of(instruction)));
    snapshot.put("outstandingQuantity", 100L);
    contract = LoanContractView.builder()
        .requestReference("REQ-001")
        .transactionReference("TX-001")
        .contractSnapshot(snapshot)
        .build();
  }

  @Test
  void recordsCustodianReceiptWithoutChangingSettledBalances() {
    stubRepositories();

    handler.handle(receipt());

    Map<String, Object> instruction = returnInstruction();
    assertThat(instruction.get("status")).isEqualTo("PENDING");
    assertThat(instruction.get("deliveryStatus")).isEqualTo("DELIVERED");
    assertThat(instruction.get("settlementAggregateVersion")).isEqualTo(1L);
    assertThat(contract.getContractSnapshot().get("outstandingQuantity")).isEqualTo(100L);
    verify(contracts).save(contract);
  }

  @Test
  void duplicateReceiptIsIdempotent() {
    stubRepositories();
    EventEnvelope event = receipt();

    handler.handle(event);
    handler.handle(event);

    verify(contracts, times(1)).save(contract);
  }

  @Test
  void rejectsReceiptFromAnotherCustodian() {
    EventEnvelope event = receipt();
    event.setSenderOrganizationReference("CUSTODIAN-OTHER");

    assertThatThrownBy(() -> handler.handle(event))
        .isInstanceOf(InvalidEnvelopeException.class)
        .hasMessageContaining("RECEIPT_ENVELOPE_MISMATCH");
  }

  private void stubRepositories() {
    when(contracts.findLockedByTransactionReference("TX-001"))
        .thenReturn(Optional.of(contract));
    when(requests.findByRequestReference("REQ-001")).thenReturn(Optional.of(LoanRequest.builder()
        .requestReference("REQ-001")
        .borrowerOrganizationReference("BORROWER-001")
        .custodianOrganizationReference("CUSTODIAN-001")
        .securityIsin("INE002A01018")
        .settlementCurrency("INR")
        .build()));
  }

  private EventEnvelope receipt() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("aggregateVersion", 1L);
    payload.put("transactionReference", "TX-001");
    payload.put("instructionReference", INSTRUCTION_REFERENCE);
    payload.put("instructionType", "SECURITY_RETURN");
    payload.put("borrowerOrganizationReference", "BORROWER-001");
    payload.put("custodianOrganizationReference", "CUSTODIAN-001");
    payload.put("settlementStatus", "PENDING");
    payload.put("instructionStatus", "PENDING");
    payload.put("securityIsin", "INE002A01018");
    payload.put("quantity", 10L);
    payload.put("currency", "INR");
    payload.put("recallReference", null);
    return EventEnvelope.builder()
        .eventId("b00f6c45-c1db-4a64-a585-d7b34bdbb499")
        .eventType("SECURITY_RETURN_RECEIVED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(INSTRUCTION_REFERENCE)
        .aggregateReference(INSTRUCTION_REFERENCE)
        .aggregateVersion(1L)
        .occurredAt(NOW)
        .senderOrganizationReference("CUSTODIAN-001")
        .receiverOrganizationReference("BORROWER-001")
        .payload(payload)
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> returnInstruction() {
    List<Map<String, Object>> instructions =
        (List<Map<String, Object>>) contract.getContractSnapshot().get("returnInstructions");
    return instructions.getFirst();
  }
}
