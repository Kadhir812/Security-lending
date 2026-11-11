package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.InventoryMovementRepository;
import com.hexaware.custodian_service.repository.LenderInventoryRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.RecallRequestRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.margin.MarginCallService;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import com.hexaware.custodian_service.service.returns.SecuritiesReturnService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class SecuritiesReturnServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
  private static final String INSTRUCTION_REFERENCE = "RET-001";
  @Mock private SetupAccess access;
  @Mock private LoanContractRepository contracts;
  @Mock private LoanRequestRepository requests;
  @Mock private MslaAgreementRepository agreements;
  @Mock private LenderMandateRepository mandates;
  @Mock private OrganizationRepository organizations;
  @Mock private RecallRequestRepository recalls;
  @Mock private SettlementInstructionRepository settlements;
  @Mock private LenderInventoryRepository inventories;
  @Mock private InventoryMovementRepository movements;
  @Mock private OutgoingEventService outgoing;
  @Mock private MarginCallService marginCalls;
  @Mock private PlatformTransactionManager transactionManager;
  private SecuritiesReturnService service;
  private LoanContract contract;
  private LoanRequest request;
  private MslaAgreement agreement;
  private LenderMandate mandate;
  private AtomicReference<SettlementInstruction> storedInstruction;

  @BeforeEach
  void setUp() {
    storedInstruction = new AtomicReference<>();
    service = new SecuritiesReturnService(
        access, contracts, requests, agreements, mandates, organizations, recalls, settlements,
        inventories, movements, outgoing, marginCalls, Clock.fixed(NOW, ZoneOffset.UTC),
        transactionManager);
    contract = LoanContract.builder()
        .contractId(20L)
        .transactionReference("TX-001")
        .loanRequestId(30L)
        .deliveredQuantity(100L)
        .returnedQuantity(0L)
        .outstandingQuantity(100L)
        .contractStatus(LoanContractStatus.ON_LOAN)
        .settlementCurrency("INR")
        .build();
    request = LoanRequest.builder()
        .requestId(30L)
        .mslaId(40L)
        .securityIsin("INE002A01018")
        .settlementCurrency("INR")
        .build();
    agreement = MslaAgreement.builder()
        .mslaId(40L)
        .mandateId(50L)
        .borrowerOrgId(2L)
        .agreementStatus(AgreementStatus.ACTIVE)
        .build();
    mandate = LenderMandate.builder()
        .mandateId(50L)
        .lenderOrgId(3L)
        .custodianOrgId(1L)
        .status(MandateStatus.ACTIVE)
        .build();
  }

  @Test
  void storesReturnAsPendingAndPublishesOnlyOnceOnDuplicateDelivery() {
    stubRepositories();
    EventEnvelope event = submittedReturn();

    service.receiveReturnInstruction(event);
    service.receiveReturnInstruction(event);

    assertThat(storedInstruction.get()).isNotNull();
    assertThat(storedInstruction.get().getInstructionType()).isEqualTo("SECURITY_RETURN");
    assertThat(storedInstruction.get().getQuantity()).isEqualByComparingTo("10");
    assertThat(storedInstruction.get().getStatus().name()).isEqualTo("PENDING");
    verify(settlements, times(1)).save(any(SettlementInstruction.class));
    verify(outgoing, times(1)).enqueue(any(), any(EventEnvelope.class));
  }

  @Test
  void rejectsReturnSubmissionFromAnotherBorrower() {
    EventEnvelope event = submittedReturn();
    event.setSenderOrganizationReference("BORROWER-OTHER");

    assertThatThrownBy(() -> service.receiveReturnInstruction(event))
        .isInstanceOf(InvalidEnvelopeException.class)
        .hasMessageContaining("ENVELOPE_MISMATCH");
  }

  private void stubRepositories() {
    when(contracts.findLockedByTransactionReference("TX-001"))
        .thenReturn(Optional.of(contract));
    when(requests.findById(30L)).thenReturn(Optional.of(request));
    when(agreements.findById(40L)).thenReturn(Optional.of(agreement));
    when(mandates.findById(50L)).thenReturn(Optional.of(mandate));
    when(organizations.findById(1L)).thenReturn(Optional.of(organization(1L, "CUSTODIAN-001")));
    when(organizations.findById(2L)).thenReturn(Optional.of(organization(2L, "BORROWER-001")));
    when(organizations.findById(3L)).thenReturn(Optional.of(organization(3L, "LENDER-001")));
    when(settlements.findByInstructionReference(INSTRUCTION_REFERENCE))
        .thenAnswer(invocation -> Optional.ofNullable(
            storedInstruction.get()));
    when(settlements.findByContractId(20L)).thenReturn(java.util.List.of());
    doAnswer(invocation -> {
      SettlementInstruction instruction = invocation.getArgument(0);
      instruction.setVersion(0L);
      storedInstruction.set(instruction);
      return instruction;
    }).when(settlements).save(any(SettlementInstruction.class));
  }

  private EventEnvelope submittedReturn() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", "TX-001");
    payload.put("instructionReference", INSTRUCTION_REFERENCE);
    payload.put("recallReference", null);
    payload.put("quantity", 10L);
    payload.put("securityIsin", "INE002A01018");
    payload.put("currency", "INR");
    payload.put("borrowerOrganizationReference", "BORROWER-001");
    payload.put("custodianOrganizationReference", "CUSTODIAN-001");
    return EventEnvelope.builder()
        .eventId("b00f6c45-c1db-4a64-a585-d7b34bdbb499")
        .eventType("SECURITY_RETURN_SUBMITTED")
        .schemaVersion(1)
        .producerService("BORROWER")
        .destinationService("CUSTODIAN")
        .correlationId(INSTRUCTION_REFERENCE)
        .aggregateReference(INSTRUCTION_REFERENCE)
        .aggregateVersion(0L)
        .occurredAt(NOW)
        .senderOrganizationReference("BORROWER-001")
        .receiverOrganizationReference("CUSTODIAN-001")
        .payload(payload)
        .build();
  }

  private Organization organization(Long id, String reference) {
    return Organization.builder()
        .organizationId(id)
        .organizationReference(reference)
        .build();
  }
}
