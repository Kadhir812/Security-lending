package com.hexaware.custodian_service.service.fees;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LendingFeePayment;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LendingFeePaymentRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.cash.LenderCashLedgerService;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LendingFeePaymentServiceTest {
  private static final String TRANSACTION_REFERENCE = "CTR-RETURNED";
  private static final String PAYMENT_REFERENCE = "PAY-FINAL-001";
  private static final String RESULT_REFERENCE = "22c2650e-1bc2-4728-883c-985bfec94889";
  private static final String ITEM_KEY = "LENDING_FEE:2026-11-15";
  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

  private final SetupAccess access = mock(SetupAccess.class);
  private final LoanContractRepository contracts = mock(LoanContractRepository.class);
  private final LoanRequestRepository requests = mock(LoanRequestRepository.class);
  private final MslaAgreementRepository agreements = mock(MslaAgreementRepository.class);
  private final LenderMandateRepository mandates = mock(LenderMandateRepository.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final LendingFeePaymentRepository payments =
      mock(LendingFeePaymentRepository.class);
  private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
  private final LendingFeeAccrualService accruals = mock(LendingFeeAccrualService.class);
  private final OutgoingEventService outgoing = mock(OutgoingEventService.class);
  private final LenderCashLedgerService cashLedger = mock(LenderCashLedgerService.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private LendingFeePaymentService service;
  private LoanContract contract;

  @BeforeEach
  void setUp() {
    LendingFeePaymentSupport support =
        new LendingFeePaymentSupport(payments, contracts, outbox, accruals, outgoing, clock);
    service =
        new LendingFeePaymentService(
            access, contracts, requests, agreements, mandates, organizations, payments, clock,
            support, cashLedger);
    contract = mock(LoanContract.class);
    when(contract.getContractId()).thenReturn(100L);
    when(contract.getTransactionReference()).thenReturn(TRANSACTION_REFERENCE);
    when(contract.getLoanRequestId()).thenReturn(200L);
    when(contract.getSettlementCurrency()).thenReturn("INR");
    when(contract.getContractStatus()).thenReturn(LoanContractStatus.RETURNED);
    when(contracts.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(contract));

    LoanRequest loanRequest = mock(LoanRequest.class);
    when(loanRequest.getMslaId()).thenReturn(300L);
    when(requests.findById(200L)).thenReturn(Optional.of(loanRequest));

    MslaAgreement agreement = mock(MslaAgreement.class);
    when(agreement.getMandateId()).thenReturn(400L);
    when(agreement.getBorrowerOrgId()).thenReturn(1L);
    when(agreements.findById(300L)).thenReturn(Optional.of(agreement));

    LenderMandate mandate = mock(LenderMandate.class);
    when(mandate.getLenderOrgId()).thenReturn(2L);
    when(mandate.getCustodianOrgId()).thenReturn(3L);
    when(mandates.findById(400L)).thenReturn(Optional.of(mandate));

    stubOrganization(1L, "BORROWER-001", OrganizationType.BORROWER);
    stubOrganization(2L, "LENDER-001", OrganizationType.LENDER);
    stubOrganization(3L, "CUSTODIAN-001", OrganizationType.CUSTODIAN);

    when(payments.findLockedByContractId(100L)).thenReturn(List.of());
    when(accruals.chargeEvents(TRANSACTION_REFERENCE))
        .thenReturn(
            List.of(
                Map.of(
                    "itemKey", ITEM_KEY,
                    "resultReference", RESULT_REFERENCE,
                    "currency", "INR",
                    "amount", "4.60")));
  }

  @Test
  void acceptsBorrowerFeePaymentForReturnedContract() {
    service.receiveSubmitted(event());

    verify(payments).save(any(LendingFeePayment.class));
    verify(payments).flush();
  }

  @Test
  void rejectsBorrowerFeePaymentForClosedContract() {
    when(contract.getContractStatus()).thenReturn(LoanContractStatus.CLOSED);

    assertThrows(InvalidEnvelopeException.class, () -> service.receiveSubmitted(event()));

    verify(payments, never()).save(any(LendingFeePayment.class));
  }

  private void stubOrganization(
      Long id, String reference, OrganizationType organizationType) {
    Organization organization = mock(Organization.class);
    when(organization.getOrganizationId()).thenReturn(id);
    when(organization.getOrganizationReference()).thenReturn(reference);
    when(organization.getOrganizationType()).thenReturn(organizationType);
    when(organization.getStatus()).thenReturn(OrganizationStatus.ACTIVE);
    when(organizations.findById(id)).thenReturn(Optional.of(organization));
  }

  private EventEnvelope event() {
    Map<String, Object> allocation =
        Map.of(
            "calculationResultReference", RESULT_REFERENCE,
            "itemKey", ITEM_KEY,
            "amount", "4.60");
    Map<String, Object> payload =
        Map.of(
            "payloadVersion", 1,
            "transactionReference", TRANSACTION_REFERENCE,
            "paymentReference", PAYMENT_REFERENCE,
            "borrowerOrganizationReference", "BORROWER-001",
            "custodianOrganizationReference", "CUSTODIAN-001",
            "amount", "4.60",
            "currency", "INR",
            "allocations", List.of(allocation));
    return EventEnvelope.builder()
        .eventId("22c2650e-1bc2-4728-883c-985bfec94888")
        .eventType("FEE_PAYMENT_SUBMITTED")
        .schemaVersion(1)
        .producerService("BORROWER")
        .destinationService("CUSTODIAN")
        .correlationId(PAYMENT_REFERENCE)
        .aggregateReference(PAYMENT_REFERENCE)
        .aggregateVersion(0L)
        .occurredAt(NOW)
        .senderOrganizationReference("BORROWER-001")
        .receiverOrganizationReference("CUSTODIAN-001")
        .payload(payload)
        .build();
  }
}
