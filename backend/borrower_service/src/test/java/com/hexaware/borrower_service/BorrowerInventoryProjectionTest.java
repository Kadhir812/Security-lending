package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.entity.BorrowerInventory;
import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.kafka.inbox.SettlementInstructionUpdatedHandler;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import com.hexaware.borrower_service.repository.BorrowerInventoryRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BorrowerInventoryProjectionTest {
  private static final Instant NOW = Instant.parse("2026-11-17T07:00:00Z");
  private static final String REQUEST_REFERENCE = "REQ-001";
  private static final String TRANSACTION_REFERENCE = "TX-001";
  private static final String BORROWER_REFERENCE = "BORROWER-001";
  private static final String CUSTODIAN_REFERENCE = "CUSTODIAN-001";
  private static final String SECURITY_ISIN = "INE002A01018";

  @Mock private LoanContractViewRepository contracts;
  @Mock private LoanRequestRepository requests;
  @Mock private BorrowerInventoryRepository inventories;

  private SettlementInstructionUpdatedHandler handler;
  private LoanContractView contract;
  private LoanRequest request;

  @BeforeEach
  void setUp() {
    handler = new SettlementInstructionUpdatedHandler(
        contracts,
        requests,
        inventories,
        new LoanRequestFixMessageBuilder(),
        Clock.fixed(NOW, ZoneOffset.UTC));
    contract = LoanContractView.builder()
        .requestReference(REQUEST_REFERENCE)
        .transactionReference(TRANSACTION_REFERENCE)
        .contractSnapshot(new LinkedHashMap<>())
        .build();
    request = LoanRequest.builder()
        .requestReference(REQUEST_REFERENCE)
        .borrowerOrganizationReference(BORROWER_REFERENCE)
        .custodianOrganizationReference(CUSTODIAN_REFERENCE)
        .securityIsin(SECURITY_ISIN)
        .settlementCurrency("INR")
        .quantity(100L)
        .build();
    when(contracts.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(contract));
    when(requests.findByRequestReference(REQUEST_REFERENCE)).thenReturn(Optional.of(request));
  }

  @Test
  void addsDeliveredSecuritiesAndDecreasesPositionOnSettledPartialReturn() {
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.empty());

    handler.handle(deliveryEvent());

    ArgumentCaptor<BorrowerInventory> created =
        ArgumentCaptor.forClass(BorrowerInventory.class);
    verify(inventories).save(created.capture());
    BorrowerInventory inventory = created.getValue();
    assertThat(inventory.getBorrowerOrganizationReference()).isEqualTo(BORROWER_REFERENCE);
    assertThat(inventory.getSecurityIsin()).isEqualTo(SECURITY_ISIN);
    assertThat(inventory.getBorrowedQuantity()).isEqualTo(100L);
    assertThat(inventory.getSourceContractVersion()).isEqualTo(1L);

    Map<String, Object> returnInstruction = new LinkedHashMap<>();
    returnInstruction.put("instructionReference", "RET-001");
    returnInstruction.put("quantity", 20L);
    returnInstruction.put("recallReference", null);
    returnInstruction.put("securityIsin", SECURITY_ISIN);
    returnInstruction.put("currency", "INR");
    returnInstruction.put("status", "PENDING");
    returnInstruction.put("settlementAggregateVersion", 1L);
    contract.getContractSnapshot().put(
        "returnInstructions", new ArrayList<>(List.of(returnInstruction)));
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(inventory));

    handler.handle(returnEvent());

    assertThat(inventory.getBorrowedQuantity()).isEqualTo(80L);
    assertThat(inventory.getSourceContractVersion()).isEqualTo(2L);
    verify(inventories, org.mockito.Mockito.times(2)).save(any(BorrowerInventory.class));
  }

  @Test
  void rejectsInventoryUpdatesForAnotherBorrowerOrSecurity() {
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(BorrowerInventory.builder()
            .transactionReference(TRANSACTION_REFERENCE)
            .borrowerOrganizationReference("BORROWER-OTHER")
            .securityIsin(SECURITY_ISIN)
            .borrowedQuantity(100L)
            .sourceContractVersion(1L)
            .version(0L)
            .build()));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle(deliveryEvent()))
        .isInstanceOf(com.hexaware.borrower_service.exception.InvalidEnvelopeException.class)
        .hasMessageContaining("BORROWER_INVENTORY_CONTRACT_TERMS_MISMATCH");
  }

  @Test
  void duplicateDeliveryDoesNotAddThePositionTwice() {
    Map<String, Object> priorDelivery = new LinkedHashMap<>();
    priorDelivery.put("instructionReference", "DEL-001");
    priorDelivery.put("settlementAggregateVersion", 1L);
    priorDelivery.put("settlementStatus", "SETTLED");
    priorDelivery.put("externalConfirmationReference", "CONF-DEL-001");
    contract.setContractSnapshot(new LinkedHashMap<>(Map.of(
        "settlementContractVersion", 1L,
        "deliveredQuantity", 100L,
        "outstandingQuantity", 100L,
        "settlementInstructions", new ArrayList<>(List.of(priorDelivery)))));
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(BorrowerInventory.builder()
            .transactionReference(TRANSACTION_REFERENCE)
            .borrowerOrganizationReference(BORROWER_REFERENCE)
            .securityIsin(SECURITY_ISIN)
            .borrowedQuantity(100L)
            .sourceContractVersion(1L)
            .version(0L)
            .build()));

    handler.handle(deliveryEvent());

    verify(inventories, never()).save(any(BorrowerInventory.class));
  }

  private EventEnvelope deliveryEvent() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("aggregateVersion", 1L);
    payload.put("transactionReference", TRANSACTION_REFERENCE);
    payload.put("instructionReference", "DEL-001");
    payload.put("instructionType", "SECURITY_DELIVERY");
    payload.put("borrowerOrganizationReference", BORROWER_REFERENCE);
    payload.put("custodianOrganizationReference", CUSTODIAN_REFERENCE);
    payload.put("settlementStatus", "SETTLED");
    payload.put("instructionStatus", "SETTLED");
    payload.put("externalConfirmationReference", "CONF-DEL-001");
    payload.put("simulatedConfirmation", true);
    payload.put("securityIsin", SECURITY_ISIN);
    payload.put("quantity", 100L);
    payload.put("currency", "INR");
    payload.put("contractStatus", "ON_LOAN");
    payload.put("contractVersion", 1L);
    payload.put("deliveredQuantity", 100L);
    payload.put("outstandingQuantity", 100L);
    payload.put("confirmedCashCollateral", "105000.00");
    payload.put("settledAt", NOW.toString());
    return event("DEL-001", 1L, payload);
  }

  private EventEnvelope returnEvent() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("aggregateVersion", 2L);
    payload.put("transactionReference", TRANSACTION_REFERENCE);
    payload.put("instructionReference", "RET-001");
    payload.put("instructionType", "SECURITY_RETURN");
    payload.put("borrowerOrganizationReference", BORROWER_REFERENCE);
    payload.put("custodianOrganizationReference", CUSTODIAN_REFERENCE);
    payload.put("settlementStatus", "SETTLED");
    payload.put("instructionStatus", "SETTLED");
    payload.put("externalConfirmationReference", "CONF-RET-001");
    payload.put("simulatedConfirmation", true);
    payload.put("securityIsin", SECURITY_ISIN);
    payload.put("quantity", 20L);
    payload.put("currency", "INR");
    payload.put("recallReference", null);
    payload.put("contractStatus", "ON_LOAN");
    payload.put("contractVersion", 2L);
    payload.put("deliveredQuantity", 100L);
    payload.put("returnedQuantity", 20L);
    payload.put("outstandingQuantity", 80L);
    payload.put("settledAt", NOW.toString());
    return event("RET-001", 2L, payload);
  }

  private EventEnvelope event(String reference, long version, Map<String, Object> payload) {
    return EventEnvelope.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("SETTLEMENT_INSTRUCTION_UPDATED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(reference)
        .aggregateReference(reference)
        .aggregateVersion(version)
        .occurredAt(NOW)
        .senderOrganizationReference(CUSTODIAN_REFERENCE)
        .receiverOrganizationReference(BORROWER_REFERENCE)
        .payload(payload)
        .build();
  }
}
