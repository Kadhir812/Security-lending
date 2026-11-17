package com.hexaware.borrower_service.service.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class RebateProjectionServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
  private static final String TRANSACTION_REFERENCE = "TX-REBATE-001";
  private static final String ITEM_KEY = "REBATE:2026-11-16";

  @Mock private LoanContractViewRepository contractViews;
  @Mock private LoanRequestRepository requests;

  private RebateProjectionService service;
  private LoanContractView view;

  @BeforeEach
  void setUp() {
    service =
        new RebateProjectionService(
            contractViews, requests, Clock.fixed(NOW, ZoneOffset.UTC));
    view =
        LoanContractView.builder()
            .requestReference("REQ-REBATE-001")
            .transactionReference(TRANSACTION_REFERENCE)
            .contractSnapshot(new LinkedHashMap<>())
            .receivedAt(NOW)
            .build();
    when(contractViews.findByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(view));
    when(requests.findByRequestReference("REQ-REBATE-001"))
        .thenReturn(
            Optional.of(
                LoanRequest.builder()
                    .requestReference("REQ-REBATE-001")
                    .borrowerOrganizationReference("BORROWER-001")
                    .custodianOrganizationReference("CUSTODIAN-001")
                    .settlementCurrency("INR")
                    .build()));
  }

  @Test
  void projectsAccrualAndLinkedCorrectionInAggregateOrder() {
    service.applyAccrual(event(1, "RESULT-001", null, "0.86"));
    service.applyAccrual(event(2, "RESULT-002", "RESULT-001", "-0.01"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> accruals =
        (List<Map<String, Object>>) view.getContractSnapshot().get("rebateAccruals");
    assertThat(accruals)
        .extracting(row -> row.get("resultReference"))
        .containsExactly("RESULT-001", "RESULT-002");
    assertThat(view.getContractSnapshot().get("rebateAccrualAggregateVersion")).isEqualTo(2L);
    verify(contractViews, org.mockito.Mockito.times(2)).save(view);
  }

  @Test
  void projectsPendingPayoutThenPaidConfirmation() {
    service.applyPayment(paymentEvent(1, "PENDING", null));
    service.applyPayment(paymentEvent(2, "PAID", "BANK-001"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> payments =
        (List<Map<String, Object>>) view.getContractSnapshot().get("rebatePayments");
    assertThat(payments).hasSize(1);
    assertThat(payments.getFirst())
        .containsEntry("paymentReference", "PAY-REBATE-001")
        .containsEntry("status", "PAID")
        .containsEntry("externalConfirmationReference", "BANK-001");
    assertThat(view.getContractSnapshot().get("rebatePaymentAggregateVersion")).isEqualTo(2L);
    verify(contractViews, org.mockito.Mockito.times(2)).save(view);
  }

  private EventEnvelope paymentEvent(
      long aggregateVersion, String status, String externalReference) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", TRANSACTION_REFERENCE);
    payload.put("paymentReference", "PAY-REBATE-001");
    payload.put("obligationReference", ITEM_KEY);
    payload.put("itemKey", ITEM_KEY);
    payload.put("accrualDate", "2026-11-16");
    payload.put("calculationResultReference", "22c2650e-1bc2-4728-883c-985bfec94889");
    payload.put("borrowerOrganizationReference", "BORROWER-001");
    payload.put("custodianOrganizationReference", "CUSTODIAN-001");
    payload.put("amount", "0.86");
    payload.put("currency", "INR");
    payload.put("status", status);
    payload.put("aggregateVersion", aggregateVersion);
    payload.put("externalConfirmationReference", externalReference);
    payload.put("failureReason", null);
    return EventEnvelope.builder()
        .eventId(
            aggregateVersion == 1
                ? "b00f6c45-c1db-4a64-a585-d7b34bdbb499"
                : "b00f6c45-c1db-4a64-a585-d7b34bdbb498")
        .eventType("REBATE_PAYMENT_UPDATED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId("PAY-REBATE-001")
        .causationEventId("b00f6c45-c1db-4a64-a585-d7b34bdbb497")
        .aggregateReference("PAY-REBATE-001")
        .aggregateVersion(aggregateVersion)
        .occurredAt(NOW)
        .senderOrganizationReference("CUSTODIAN-001")
        .receiverOrganizationReference("BORROWER-001")
        .payload(payload)
        .build();
  }

  private EventEnvelope event(
      long aggregateVersion, String resultReference, String supersedes, String amount) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", TRANSACTION_REFERENCE);
    payload.put("resultReference", resultReference);
    payload.put("supersedesResultReference", supersedes);
    payload.put("borrowerOrganizationReference", "BORROWER-001");
    payload.put("custodianOrganizationReference", "CUSTODIAN-001");
    payload.put("status", "POSTED");
    payload.put("currency", "INR");
    payload.put("amount", amount);
    payload.put("grossAmount", "0.86");
    payload.put("settledCashCollateralAmount", "10000.00");
    payload.put("annualRebatePercentage", "3.0");
    payload.put("accrualDate", "2026-11-16");
    payload.put("itemKey", ITEM_KEY);
    payload.put("dayCountBasis", "ACT_365");
    payload.put("accrualBasis", "SETTLED_CASH_COLLATERAL");
    payload.put("inputVersion", "input-" + aggregateVersion);
    return EventEnvelope.builder()
        .eventId(
            aggregateVersion == 1
                ? "b00f6c45-c1db-4a64-a585-d7b34bdbb499"
                : "b00f6c45-c1db-4a64-a585-d7b34bdbb498")
        .eventType("REBATE_ACCRUAL_UPDATED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(TRANSACTION_REFERENCE)
        .aggregateReference(TRANSACTION_REFERENCE)
        .aggregateVersion(aggregateVersion)
        .occurredAt(NOW)
        .senderOrganizationReference("CUSTODIAN-001")
        .receiverOrganizationReference("BORROWER-001")
        .payload(payload)
        .build();
  }
}
