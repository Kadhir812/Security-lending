package com.hexaware.borrower_service.service.fees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.dto.request.FeePaymentAllocationRequest;
import com.hexaware.borrower_service.dto.request.FeePaymentRequest;
import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class FeePaymentServiceTest {
  private static final String TRANSACTION_REFERENCE = "CTR-RETURNED";
  private static final String REQUEST_REFERENCE = "LR-RETURNED";
  private static final String PAYMENT_REFERENCE = "PAY-FINAL-001";
  private static final String RESULT_REFERENCE = "22c2650e-1bc2-4728-883c-985bfec94889";
  private static final String ITEM_KEY = "LENDING_FEE:2026-11-15";
  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

  private final LoanContractViewRepository contractViews =
      mock(LoanContractViewRepository.class);
  private final LoanRequestRepository requests = mock(LoanRequestRepository.class);
  private final OutgoingEventService outgoing = mock(OutgoingEventService.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private FeePaymentService service;
  private LocalUserAuthentication authentication;
  private LoanRequest loanRequest;

  @BeforeEach
  void setUp() {
    service = new FeePaymentService(contractViews, requests, outgoing, clock);
    Jwt token =
        Jwt.withTokenValue("borrower-token")
            .header("alg", "RS256")
            .subject("borrower-user")
            .issuedAt(NOW)
            .expiresAt(NOW.plusSeconds(600))
            .claim("organization_reference", "BORROWER-001")
            .build();
    authentication =
        new LocalUserAuthentication(
            token,
            List.of(new SimpleGrantedAuthority("ROLE_TRADER")),
            1L,
            "BORROWER-001");
    loanRequest = mock(LoanRequest.class);
    when(loanRequest.getSettlementCurrency()).thenReturn("INR");
    when(loanRequest.getCustodianOrganizationReference()).thenReturn("CUSTODIAN-001");
    when(requests.findByRequestReferenceAndBorrowerOrganizationReference(
            REQUEST_REFERENCE, "BORROWER-001"))
        .thenReturn(Optional.of(loanRequest));
  }

  @Test
  void submitsFinalFeePaymentForReturnedContract() {
    LoanContractView view = contractView("RETURNED");
    when(contractViews.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(view));

    Map<String, Object> response =
        service.submit(authentication, TRANSACTION_REFERENCE, PAYMENT_REFERENCE, request());

    assertEquals("SUBMITTED", response.get("status"));
    verify(outgoing).enqueue(eq("FEE_PAYMENT"), any(EventEnvelope.class));
  }

  @Test
  void rejectsFeePaymentAfterContractIsClosed() {
    LoanContractView view = contractView("CLOSED");
    when(contractViews.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(view));

    assertThrows(
        BorrowerException.class,
        () -> service.submit(
            authentication, TRANSACTION_REFERENCE, PAYMENT_REFERENCE, request()));

    verify(outgoing, never()).enqueue(any(), any(EventEnvelope.class));
  }

  private LoanContractView contractView(String status) {
    Map<String, Object> charge =
        Map.of(
            "itemKey", ITEM_KEY,
            "resultReference", RESULT_REFERENCE,
            "currency", "INR",
            "amount", "4.60");
    Map<String, Object> snapshot =
        new java.util.LinkedHashMap<>(
            Map.of(
                "contractStatus", status,
                "feeCharges", List.of(charge),
                "feePayments", new ArrayList<>()));
    return LoanContractView.builder()
        .requestReference(REQUEST_REFERENCE)
        .transactionReference(TRANSACTION_REFERENCE)
        .contractSnapshot(snapshot)
        .receivedAt(NOW)
        .build();
  }

  private FeePaymentRequest request() {
    return FeePaymentRequest.builder()
        .amount(new BigDecimal("4.60"))
        .currency("INR")
        .allocations(
            List.of(
                FeePaymentAllocationRequest.builder()
                    .calculationResultReference(RESULT_REFERENCE)
                    .itemKey(ITEM_KEY)
                    .amount(new BigDecimal("4.60"))
                    .build()))
        .build();
  }
}
