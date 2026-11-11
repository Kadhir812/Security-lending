package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.kafka.inbox.ContractClosedHandler;
import com.hexaware.borrower_service.kafka.inbox.SettlementInstructionUpdatedHandler;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import java.nio.charset.StandardCharsets;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinalReleaseProjectionTest {
  private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
  private static final String TRANSACTION_REFERENCE = "TX-001";
  private static final String INSTRUCTION_REFERENCE = "R:REL-001:60";

  @Mock private LoanContractViewRepository contracts;
  @Mock private LoanRequestRepository requests;
  private LoanContractView contract;
  private SettlementInstructionUpdatedHandler settlements;
  private ContractClosedHandler closures;

  @BeforeEach
  void setUp() {
    settlements = new SettlementInstructionUpdatedHandler(contracts, requests, Clock.fixed(NOW, ZoneOffset.UTC));
    closures = new ContractClosedHandler(contracts, requests, Clock.fixed(NOW, ZoneOffset.UTC));
    Map<String, Object> releaseRequest = new LinkedHashMap<>();
    releaseRequest.put("releaseReference", "REL-001");
    releaseRequest.put("status", "SUBMITTED");
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("contractStatus", "RETURNED");
    snapshot.put("settlementContractVersion", 2L);
    snapshot.put("confirmedCashCollateral", "100.00");
    snapshot.put("collateralReleaseRequests", new ArrayList<>(List.of(releaseRequest)));
    contract = LoanContractView.builder()
        .requestReference("REQ-001")
        .transactionReference(TRANSACTION_REFERENCE)
        .contractSnapshot(snapshot)
        .build();
    when(contracts.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(contract));
    when(requests.findByRequestReference("REQ-001")).thenReturn(Optional.of(
        LoanRequest.builder()
            .requestReference("REQ-001")
            .borrowerOrganizationReference("BORROWER-001")
            .custodianOrganizationReference("CUSTODIAN-001")
            .settlementCurrency("INR")
            .build()));
  }

  @Test
  void appliesConfirmedReleaseAndIgnoresStalePayoutUpdates() {
    EventEnvelope pending = releaseEvent(1L, "PENDING", null, null, false);
    EventEnvelope settled = releaseEvent(2L, "SETTLED", "PAYOUT-001", null, true);

    settlements.handle(pending);
    settlements.handle(settled);
    settlements.handle(pending);

    Map<String, Object> release = releaseRequest();
    assertThat(release.get("status")).isEqualTo("SETTLED");
    assertThat(release.get("amount")).isEqualTo("100.00");
    assertThat(contract.getContractSnapshot().get("confirmedCashCollateral")).isEqualTo("0.00");
    verify(contracts, times(2)).save(contract);
  }

  @Test
  void rejectsReleaseUpdateFromAnotherCustodian() {
    EventEnvelope event = releaseEvent(1L, "PENDING", null, null, false);
    event.setSenderOrganizationReference("CUSTODIAN-OTHER");

    assertThatThrownBy(() -> settlements.handle(event))
        .isInstanceOf(InvalidEnvelopeException.class)
        .hasMessageContaining("ENVELOPE_MISMATCH");
    verify(contracts, never()).save(contract);
  }

  @Test
  void appliesClosureOnceAndRejectsStaleStateReversal() {
    EventEnvelope closed = closureEvent();

    closures.handle(closed);
    settlements.handle(releaseEvent(1L, "PENDING", null, null, false));
    closures.handle(closed);

    assertThat(contract.getContractSnapshot().get("contractStatus")).isEqualTo("CLOSED");
    assertThat(contract.getContractSnapshot().get("closureReference")).isEqualTo("CLOSE-001");
    assertThat(releaseRequest().get("status")).isEqualTo("COMPLETED");
    verify(contracts, times(2)).save(contract);
  }

  private EventEnvelope releaseEvent(
      long version, String status, String confirmationReference, String failureReason,
      boolean simulatedConfirmation) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("aggregateVersion", version);
    payload.put("transactionReference", TRANSACTION_REFERENCE);
    payload.put("instructionReference", INSTRUCTION_REFERENCE);
    payload.put("instructionType", "COLLATERAL_RELEASE");
    payload.put("releaseReference", "REL-001");
    payload.put("releaseInstructionCount", 1);
    payload.put("borrowerOrganizationReference", "BORROWER-001");
    payload.put("custodianOrganizationReference", "CUSTODIAN-001");
    payload.put("settlementStatus", status);
    payload.put("instructionStatus", status);
    payload.put("amount", "100.00");
    payload.put("currency", "INR");
    payload.put("settledAt", status.equals("SETTLED") ? NOW.toString() : null);
    payload.put("externalConfirmationReference", confirmationReference);
    payload.put("failureReason", failureReason);
    payload.put("simulatedConfirmation", simulatedConfirmation);
    return EventEnvelope.builder()
        .eventId(UUID.nameUUIDFromBytes(
            ("SETTLEMENT_INSTRUCTION_UPDATED:" + INSTRUCTION_REFERENCE + ":" + version)
                .getBytes(StandardCharsets.UTF_8)).toString())
        .eventType("SETTLEMENT_INSTRUCTION_UPDATED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(INSTRUCTION_REFERENCE)
        .aggregateReference(INSTRUCTION_REFERENCE)
        .aggregateVersion(version)
        .occurredAt(NOW)
        .senderOrganizationReference("CUSTODIAN-001")
        .receiverOrganizationReference("BORROWER-001")
        .payload(payload)
        .build();
  }

  private EventEnvelope closureEvent() {
    String closureReference = "CLOSE-001";
    String eventId = UUID.nameUUIDFromBytes(
        ("CONTRACT_CLOSED:" + TRANSACTION_REFERENCE + ":" + closureReference)
            .getBytes(StandardCharsets.UTF_8)).toString();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", TRANSACTION_REFERENCE);
    payload.put("closureReference", closureReference);
    payload.put("reason", "final payout confirmed");
    payload.put("contractStatus", "CLOSED");
    payload.put("contractVersion", 3L);
    payload.put("deliveredQuantity", 100L);
    payload.put("returnedQuantity", 100L);
    payload.put("outstandingQuantity", 0L);
    payload.put("confirmedCashCollateral", "0.00");
    payload.put("borrowerOrganizationReference", "BORROWER-001");
    payload.put("custodianOrganizationReference", "CUSTODIAN-001");
    payload.put("closedAt", NOW.toString());
    return EventEnvelope.builder()
        .eventId(eventId)
        .eventType("CONTRACT_CLOSED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(TRANSACTION_REFERENCE)
        .aggregateReference(TRANSACTION_REFERENCE)
        .aggregateVersion(3L)
        .occurredAt(NOW)
        .senderOrganizationReference("CUSTODIAN-001")
        .receiverOrganizationReference("BORROWER-001")
        .payload(payload)
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> releaseRequest() {
    List<Map<String, Object>> rows =
        (List<Map<String, Object>>) contract.getContractSnapshot().get("collateralReleaseRequests");
    return rows.get(0);
  }
}
