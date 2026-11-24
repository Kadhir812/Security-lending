package com.hexaware.borrower_service.service.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.service.cash.BorrowerCashAccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeeProjectionServiceTest {
  private static final String TRANSACTION_REFERENCE = "CTR-TEST";
  private static final String PAYMENT_REFERENCE = "PAY-TEST";
  private static final String RESULT_REFERENCE = "RESULT-TEST";
  private static final String BORROWER_REFERENCE = "BORROWER-TEST";
  private static final String CUSTODIAN_REFERENCE = "CUSTODIAN-TEST";

  @Test
  void paidEventAcceptsEquivalentAllocationAmountsWithDifferentScale() {
    Map<String, Object> submittedAllocation = allocation("4.6");
    Map<String, Object> updatedAllocation = allocation("4.60");
    Map<String, Object> payment = new LinkedHashMap<>();
    payment.put("paymentReference", PAYMENT_REFERENCE);
    payment.put("amount", "4.6");
    payment.put("currency", "INR");
    payment.put("allocations", List.of(submittedAllocation));
    payment.put("status", "PENDING");
    payment.put("aggregateVersion", 1);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("feePayments", new ArrayList<>(List.of(payment)));

    LoanContractView view =
        LoanContractView.builder()
            .requestReference("REQ-TEST")
            .transactionReference(TRANSACTION_REFERENCE)
            .contractSnapshot(snapshot)
            .build();
    LoanRequest request =
        LoanRequest.builder()
            .requestReference("REQ-TEST")
            .borrowerOrganizationReference(BORROWER_REFERENCE)
            .custodianOrganizationReference(CUSTODIAN_REFERENCE)
            .settlementCurrency("INR")
            .build();
    LoanContractViewRepository views = mock(LoanContractViewRepository.class);
    LoanRequestRepository requests = mock(LoanRequestRepository.class);
    BorrowerCashAccountService cashAccounts = mock(BorrowerCashAccountService.class);
    when(views.findByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(view));
    when(requests.findByRequestReference("REQ-TEST")).thenReturn(Optional.of(request));
    FeeProjectionService service =
        new FeeProjectionService(
            views,
            requests,
            cashAccounts,
            Clock.fixed(Instant.parse("2026-11-16T06:00:00Z"), ZoneOffset.UTC));

    assertDoesNotThrow(
        () -> service.applyPayment(paidEvent(updatedAllocation), "FEE_PAYMENT_UPDATED"));

    Map<?, ?> projectedPayment =
        (Map<?, ?>) ((List<?>) view.getContractSnapshot().get("feePayments")).get(0);
    assertEquals("PAID", projectedPayment.get("status"));
    assertEquals(2L, ((Number) projectedPayment.get("aggregateVersion")).longValue());
  }

  private static Map<String, Object> allocation(String amount) {
    Map<String, Object> allocation = new LinkedHashMap<>();
    allocation.put("amount", amount);
    allocation.put("itemKey", "LENDING_FEE:2026-11-15");
    allocation.put("calculationResultReference", RESULT_REFERENCE);
    return allocation;
  }

  private static EventEnvelope paidEvent(Map<String, Object> allocation) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", TRANSACTION_REFERENCE);
    payload.put("paymentReference", PAYMENT_REFERENCE);
    payload.put("borrowerOrganizationReference", BORROWER_REFERENCE);
    payload.put("custodianOrganizationReference", CUSTODIAN_REFERENCE);
    payload.put("amount", "4.60");
    payload.put("currency", "INR");
    payload.put("allocations", List.of(allocation));
    payload.put("status", "PAID");
    payload.put("externalConfirmationReference", "CONFIRM-TEST");
    payload.put("failureReason", null);
    return EventEnvelope.builder()
        .eventId("0188e266-d007-7acd-9fc9-7ea41de57c55")
        .eventType("FEE_PAYMENT_UPDATED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(PAYMENT_REFERENCE)
        .causationEventId("0188e266-d007-7acd-9fc9-7ea41de57c54")
        .aggregateReference(PAYMENT_REFERENCE)
        .aggregateVersion(2L)
        .occurredAt(Instant.parse("2026-11-16T05:18:36Z"))
        .senderOrganizationReference(CUSTODIAN_REFERENCE)
        .receiverOrganizationReference(BORROWER_REFERENCE)
        .payload(payload)
        .build();
  }
}
