package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.enums.LoanRequestStatus;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.kafka.inbox.LoanRequestDecisionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanRequestDecisionHandlerTest {
  private static final Instant NOW = Instant.parse("2026-11-08T05:30:00Z");
  @Mock LoanRequestRepository requests;
  @Mock LoanContractViewRepository contracts;
  private LoanRequestDecisionHandler handler;
  private LoanRequest request;

  @BeforeEach
  void setUp() {
    handler =
        new LoanRequestDecisionHandler(
            requests, contracts, Clock.fixed(NOW, ZoneOffset.UTC));
    request =
        LoanRequest.builder()
            .requestId(10L)
            .requestReference("LR-2026-000042")
            .borrowerOrganizationReference("BORROWER-001")
            .custodianOrganizationReference("CUSTODIAN-001")
            .securityIsin("INE002A01018")
            .quantity(100L)
            .requestedSettlementDate(LocalDate.of(2026, 11, 10))
            .settlementCurrency("INR")
            .status(LoanRequestStatus.PENDING)
            .decisionSourceVersion(0L)
            .build();
    when(requests.findByRequestReference(request.getRequestReference()))
        .thenReturn(Optional.of(request));
  }

  @Test
  void appliesApprovalAndStoresItsContractSnapshot() {
    EventEnvelope event = event("APPROVED", contract());

    handler.handle(event);

    assertThat(request.getStatus()).isEqualTo(LoanRequestStatus.APPROVED);
    assertThat(request.getDecisionSourceVersion()).isEqualTo(1L);
    verify(contracts).save(org.mockito.ArgumentMatchers.argThat(
        view -> view.getTransactionReference().equals("CTR-1")));
  }

  @Test
  void ignoresStaleDecisionWithoutChangingTheNewerBorrowerView() {
    request.setStatus(LoanRequestStatus.REJECTED);
    request.setResponseReason("Newer decision");
    request.setDecisionSourceVersion(2L);

    handler.handle(event("APPROVED", contract()));

    assertThat(request.getStatus()).isEqualTo(LoanRequestStatus.REJECTED);
    assertThat(request.getResponseReason()).isEqualTo("Newer decision");
    verifyNoInteractions(contracts);
  }

  @Test
  void rejectsDecisionFromAnUnexpectedCustodian() {
    EventEnvelope event = event("REJECTED", null);
    event.setSenderOrganizationReference("CUSTODIAN-OTHER");

    assertThatThrownBy(() -> handler.handle(event))
        .isInstanceOf(InvalidEnvelopeException.class)
        .hasMessageContaining("PARTIES_OR_CORRELATION");
  }

  @Test
  void duplicateBusinessDecisionVersionDoesNotCreateAnotherContractView() {
    EventEnvelope event = event("APPROVED", contract());
    handler.handle(event);
    when(contracts.findByRequestReference(request.getRequestReference()))
        .thenReturn(
            Optional.of(
                LoanContractView.builder()
                    .requestReference(request.getRequestReference())
                    .transactionReference("CTR-1")
                    .build()));

    handler.handle(event);

    verify(contracts, times(1)).save(any(LoanContractView.class));
  }

  private EventEnvelope event(String status, Map<String, Object> contract) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("requestReference", request.getRequestReference());
    payload.put("borrowerOrganizationReference", "BORROWER-001");
    payload.put("custodianOrganizationReference", "CUSTODIAN-001");
    payload.put("decisionStatus", status);
    payload.put("decisionReason", status.equals("REJECTED") ? "Not approved" : null);
    payload.put("decisionVersion", 1L);
    payload.put("respondedAt", NOW);
    payload.put("contract", contract);
    return EventEnvelope.builder()
        .eventId("c70ab756-2e5e-45c8-a307-4b79eabf2921")
        .eventType("LOAN_REQUEST_DECISION")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(request.getRequestReference())
        .aggregateReference(request.getRequestReference())
        .aggregateVersion(1L)
        .occurredAt(NOW)
        .senderOrganizationReference("CUSTODIAN-001")
        .receiverOrganizationReference("BORROWER-001")
        .payload(payload)
        .build();
  }

  private Map<String, Object> contract() {
    Map<String, Object> contract = new LinkedHashMap<>();
    contract.put("requestReference", request.getRequestReference());
    contract.put("securityIsin", request.getSecurityIsin());
    contract.put("transactionReference", "CTR-1");
    contract.put("quantity", 100L);
    contract.put("settlementCurrency", "INR");
    contract.put("settlementDate", "2026-11-10");
    contract.put("collateralCoveragePct", 105);
    contract.put("loanMarketValue", 10000);
    contract.put("requiredCashCollateral", 10500);
    contract.put(
        "collateralTerms",
        Map.of(
            "collateralType", "CASH",
            "currency", "INR",
            "haircutPercentage", 0,
            "coveragePercentage", 105));
    contract.put(
        "acceptedValuationSnapshot",
        Map.of("currency", "INR", "priceReference", "PRICE-1", "priceSource", "TEST"));
    return contract;
  }
}
