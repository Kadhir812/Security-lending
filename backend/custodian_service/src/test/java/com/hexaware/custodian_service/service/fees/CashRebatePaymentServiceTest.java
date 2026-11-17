package com.hexaware.custodian_service.service.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.dto.request.CreateRebatePaymentRequest;
import com.hexaware.custodian_service.dto.request.RebatePaymentConfirmationRequest;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.RebatePaymentHistory;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.RebatePaymentHistoryRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class CashRebatePaymentServiceTest {
  private static final String TRANSACTION_REFERENCE = "TX-REBATE-001";
  private static final String PAYMENT_REFERENCE = "REBATE-PAY-001";
  private static final String RESULT_REFERENCE = "22c2650e-1bc2-4728-883c-985bfec94889";
  private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
  private static final FeeContractParties PARTIES =
      new FeeContractParties(1L, "BORROWER-001", 2L, "LENDER-001", 3L, "CUSTODIAN-001");

  @Mock private SetupAccess access;
  @Mock private LoanContractRepository contracts;
  @Mock private RebatePaymentHistoryRepository payments;
  @Mock private CashRebateAccrualService accruals;
  @Mock private OutgoingEventService outgoing;
  @Mock private Authentication authentication;

  private CashRebatePaymentService service;
  private LoanContract contract;

  @BeforeEach
  void setUp() {
    service =
        new CashRebatePaymentService(
            access,
            contracts,
            payments,
            accruals,
            outgoing,
            Clock.fixed(NOW, ZoneOffset.UTC));
    contract =
        LoanContract.builder()
            .contractId(100L)
            .transactionReference(TRANSACTION_REFERENCE)
            .contractStatus(LoanContractStatus.ON_LOAN)
            .settlementCurrency("INR")
            .build();
    Organization actor = Organization.builder().organizationId(3L).build();
    when(access.actor(authentication, true)).thenReturn(actor);
    when(contracts.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(contract));
    when(accruals.parties(contract)).thenReturn(PARTIES);
  }

  @Test
  void createsOnePendingPayoutForTheCurrentAccrualAndPublishesIt() {
    when(accruals.paymentAccrual(authentication, TRANSACTION_REFERENCE, RESULT_REFERENCE))
        .thenReturn(accrual());
    when(payments.findLockedByContractId(100L)).thenReturn(List.of());
    when(payments.findByPaymentReference(PAYMENT_REFERENCE)).thenReturn(Optional.empty());

    Map<String, Object> response =
        service.create(
            authentication,
            TRANSACTION_REFERENCE,
            CreateRebatePaymentRequest.builder()
                .paymentReference(PAYMENT_REFERENCE)
                .calculationResultReference(RESULT_REFERENCE)
                .build());

    assertThat(response)
        .containsEntry("status", "PENDING")
        .containsEntry("amount", "1.25")
        .containsEntry("fromOrganizationReference", "LENDER-001")
        .containsEntry("toOrganizationReference", "BORROWER-001");
    ArgumentCaptor<RebatePaymentHistory> payment =
        ArgumentCaptor.forClass(RebatePaymentHistory.class);
    verify(payments).saveAndFlush(payment.capture());
    assertThat(payment.getValue().getObligationReference()).isEqualTo("REBATE:2026-11-16");
    assertThat(payment.getValue().getPaymentAmount()).isEqualByComparingTo("1.25");
    ArgumentCaptor<EventEnvelope> event = ArgumentCaptor.forClass(EventEnvelope.class);
    verify(outgoing).enqueue(anyString(), event.capture());
    assertThat(event.getValue().getEventType()).isEqualTo("REBATE_PAYMENT_UPDATED");
    assertThat(event.getValue().getAggregateVersion()).isEqualTo(1L);
    verify(payments, never()).save(any(RebatePaymentHistory.class));
  }

  @Test
  void confirmsPendingPayoutWithExternalReferenceAndPublishesPaidState() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("paymentReference", PAYMENT_REFERENCE);
    snapshot.put("transactionReference", TRANSACTION_REFERENCE);
    snapshot.put("itemKey", "REBATE:2026-11-16");
    snapshot.put("accrualDate", "2026-11-16");
    snapshot.put("createdEventId", "b00f6c45-c1db-4a64-a585-d7b34bdbb499");
    snapshot.put("createdAt", NOW.toString());
    RebatePaymentHistory payment =
        RebatePaymentHistory.builder()
            .paymentId(1L)
            .contractId(100L)
            .obligationReference("REBATE:2026-11-16")
            .calculationSnapshot(snapshot)
            .paymentReference(PAYMENT_REFERENCE)
            .calculationResultReference(RESULT_REFERENCE)
            .paymentAmount(new java.math.BigDecimal("1.25"))
            .currency("INR")
            .status("PENDING")
            .build();
    when(payments.findLockedByContractId(100L)).thenReturn(List.of(payment));
    when(payments.findByExternalReference("BANK-REF-001")).thenReturn(Optional.empty());

    Map<String, Object> response =
        service.confirm(
            authentication,
            TRANSACTION_REFERENCE,
            PAYMENT_REFERENCE,
            RebatePaymentConfirmationRequest.builder()
                .externalConfirmationReference("BANK-REF-001")
                .build());

    assertThat(response)
        .containsEntry("status", "PAID")
        .containsEntry("externalConfirmationReference", "BANK-REF-001");
    assertThat(payment.getPaidAt()).isEqualTo(NOW);
    ArgumentCaptor<EventEnvelope> event = ArgumentCaptor.forClass(EventEnvelope.class);
    verify(outgoing).enqueue(anyString(), event.capture());
    assertThat(event.getValue().getAggregateVersion()).isEqualTo(2L);
    assertThat(event.getValue().getPayload()).containsEntry("status", "PAID");
  }

  @Test
  void rejectsASecondActivePayoutForTheSameAccrual() {
    when(accruals.paymentAccrual(authentication, TRANSACTION_REFERENCE, RESULT_REFERENCE))
        .thenReturn(accrual());
    when(payments.findLockedByContractId(100L))
        .thenReturn(
            List.of(
                RebatePaymentHistory.builder()
                    .contractId(100L)
                    .obligationReference("REBATE:2026-11-16")
                    .paymentReference("REBATE-PAY-OLD")
                    .calculationResultReference(RESULT_REFERENCE)
                    .paymentAmount(new java.math.BigDecimal("1.25"))
                    .status("PENDING")
                    .build()));
    when(payments.findByPaymentReference(PAYMENT_REFERENCE)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.create(
                    authentication,
                    TRANSACTION_REFERENCE,
                    CreateRebatePaymentRequest.builder()
                        .paymentReference(PAYMENT_REFERENCE)
                        .calculationResultReference(RESULT_REFERENCE)
                        .build()))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("already has a pending or completed payout");
    verify(payments, never()).saveAndFlush(any(RebatePaymentHistory.class));
    verify(outgoing, never()).enqueue(anyString(), any(EventEnvelope.class));
  }

  private Map<String, Object> accrual() {
    return Map.of(
        "status", "POSTED",
        "itemKey", "REBATE:2026-11-16",
        "accrualDate", "2026-11-16",
        "latestResultReference", RESULT_REFERENCE,
        "amount", "1.25",
        "currency", "INR",
        "sourceEventId", "b00f6c45-c1db-4a64-a585-d7b34bdbb499");
  }
}
