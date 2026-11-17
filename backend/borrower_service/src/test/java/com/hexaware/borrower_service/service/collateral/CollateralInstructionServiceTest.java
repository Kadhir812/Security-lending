package com.hexaware.borrower_service.service.collateral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.dto.request.CashCollateralInstructionRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class CollateralInstructionServiceTest {
  private static final String REQUEST_REFERENCE = "REQ-001";
  private static final String ORGANIZATION_REFERENCE = "BORROWER-001";

  @Mock private LoanContractViewRepository contractViews;
  @Mock private LoanRequestRepository requests;
  @Mock private OutgoingEventService outgoing;

  private CollateralInstructionService service;
  private LocalUserAuthentication authentication;
  private LoanRequest loanRequest;
  private LoanContractView view;

  @BeforeEach
  void setUp() {
    service = new CollateralInstructionService(
        contractViews,
        requests,
        outgoing,
        Clock.fixed(Instant.parse("2026-11-17T07:00:00Z"), ZoneOffset.UTC));
    authentication = new LocalUserAuthentication(
        Jwt.withTokenValue("token").header("alg", "none").claim("sub", "trader").build(),
        List.of(new SimpleGrantedAuthority("ROLE_TRADER")),
        42L,
        ORGANIZATION_REFERENCE);
    loanRequest = org.mockito.Mockito.mock(LoanRequest.class);
    when(requests.findByRequestReferenceAndBorrowerOrganizationReference(
        REQUEST_REFERENCE, ORGANIZATION_REFERENCE)).thenReturn(Optional.of(loanRequest));
    view = LoanContractView.builder()
        .requestReference(REQUEST_REFERENCE)
        .transactionReference("TX-001")
        .contractSnapshot(snapshot(List.of()))
        .build();
    when(contractViews.findLockedByRequestReference(REQUEST_REFERENCE))
        .thenReturn(Optional.of(view));
  }

  @Test
  void rejectsASecondInstructionThatWouldOverfundInitialCash() {
    view.setContractSnapshot(snapshot(List.of(instruction("CASH-001", "100.00", "SUBMITTED"))));

    assertThatThrownBy(() -> service.submit(
        authentication,
        REQUEST_REFERENCE,
        request("CASH-002", "100.00")))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("remaining initial collateral requirement");

    verify(outgoing, never()).enqueue(any(), any(EventEnvelope.class));
    verify(contractViews, never()).save(any(LoanContractView.class));
  }

  @Test
  void allowsAPartialInstructionOnlyUpToTheUnreservedAmount() {
    view.setContractSnapshot(snapshot(List.of(instruction("CASH-001", "40.00", "PENDING"))));
    when(loanRequest.getCustodianOrganizationReference()).thenReturn("CUSTODIAN-001");

    Map<String, Object> submitted = service.submit(
        authentication,
        REQUEST_REFERENCE,
        request("CASH-002", "60.00"));

    assertThat(submitted.get("instructionReference")).isEqualTo("CASH-002");
    verify(outgoing).enqueue(any(), any(EventEnvelope.class));
  }

  @Test
  void repeatedReferenceReturnsExistingInstructionWithoutEnqueueingAgain() {
    Map<String, Object> existing = instruction("CASH-001", "100.00", "SUBMITTED");
    view.setContractSnapshot(snapshot(List.of(existing)));

    Map<String, Object> result = service.submit(
        authentication,
        REQUEST_REFERENCE,
        request("CASH-001", "100.00"));

    assertThat(result).isSameAs(existing);
    verify(outgoing, never()).enqueue(any(), any(EventEnvelope.class));
  }

  private CashCollateralInstructionRequest request(String reference, String amount) {
    return new CashCollateralInstructionRequest(
        reference, new BigDecimal(amount), "INR", "CASH");
  }

  private Map<String, Object> instruction(String reference, String amount, String status) {
    Map<String, Object> instruction = new LinkedHashMap<>();
    instruction.put("instructionReference", reference);
    instruction.put("amount", amount);
    instruction.put("currency", "INR");
    instruction.put("collateralType", "CASH");
    instruction.put("status", status);
    return instruction;
  }

  private Map<String, Object> snapshot(List<Map<String, Object>> instructions) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("contractStatus", "APPROVED");
    snapshot.put("settlementCurrency", "INR");
    snapshot.put("requiredCashCollateral", "100.00");
    snapshot.put("confirmedCashCollateral", "0.00");
    snapshot.put("collateralInstructions", new ArrayList<>(instructions));
    return snapshot;
  }
}
