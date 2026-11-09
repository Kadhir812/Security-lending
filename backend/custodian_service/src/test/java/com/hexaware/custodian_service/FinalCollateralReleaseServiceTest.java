package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.ContractStatusHistory;
import com.hexaware.custodian_service.entity.InboxMessage;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.security.authentication.LocalUserAuthentication;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.InboxMessageStatus;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.config.MessagingProperties;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.ContractStatusHistoryRepository;
import com.hexaware.custodian_service.repository.InboxMessageRepository;
import com.hexaware.custodian_service.repository.LendingFeePaymentRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.MarginCallRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.RecallRequestRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.collateral.FinalCollateralReleaseService;
import com.hexaware.custodian_service.service.collateral.FinalCollateralReleaseEvents;
import com.hexaware.custodian_service.service.collateral.ContractClosureService;
import com.hexaware.custodian_service.service.fees.LendingFeeAccrualService;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinalCollateralReleaseServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
  private static final String TRANSACTION_REFERENCE = "TX-001";
  private static final String INSTRUCTION_REFERENCE = "R:REL-001:60";
  private static final String CONFIRMATION_REFERENCE = "PAYOUT-001";

  @Mock private SetupAccess access;
  @Mock private LoanContractRepository contracts;
  @Mock private LoanRequestRepository requests;
  @Mock private MslaAgreementRepository agreements;
  @Mock private LenderMandateRepository mandates;
  @Mock private OrganizationRepository organizations;
  @Mock private SettlementInstructionRepository settlements;
  @Mock private CollateralRepository collaterals;
  @Mock private RecallRequestRepository recalls;
  @Mock private MarginCallRepository marginCalls;
  @Mock private LendingFeePaymentRepository feePayments;
  @Mock private ContractStatusHistoryRepository histories;
  @Mock private InboxMessageRepository inbox;
  @Mock private MessagingProperties messaging;
  @Mock private LendingFeeAccrualService accruals;
  @Mock private OutgoingEventService outgoing;
  @Mock private Authentication authentication;
  @Mock private LocalUserAuthentication user;

  private FinalCollateralReleaseService service;
  private LoanContract contract;
  private SettlementInstruction instruction;
  private Collateral collateral;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    FinalCollateralReleaseEvents events = new FinalCollateralReleaseEvents(outgoing, clock);
    ContractClosureService closure =
        new ContractClosureService(
            contracts,
            access,
            histories,
            settlements,
            collaterals,
            recalls,
            marginCalls,
            feePayments,
            accruals,
            events,
            clock);
    service =
        new FinalCollateralReleaseService(
            access,
            contracts,
            requests,
            agreements,
            mandates,
            organizations,
            settlements,
            collaterals,
            inbox,
            messaging,
            events,
            closure,
            clock);
    contract = LoanContract.builder()
        .contractId(20L)
        .transactionReference(TRANSACTION_REFERENCE)
        .loanRequestId(30L)
        .deliveredQuantity(100L)
        .returnedQuantity(100L)
        .outstandingQuantity(0L)
        .settlementCurrency("INR")
        .contractStatus(LoanContractStatus.RETURNED)
        .version(4L)
        .build();
    instruction = SettlementInstruction.builder()
        .instructionId(70L)
        .version(0L)
        .instructionReference(INSTRUCTION_REFERENCE)
        .contractId(20L)
        .collateralId(60L)
        .instructionType("COLLATERAL_RELEASE")
        .fromOrganizationId(1L)
        .toOrganizationId(2L)
        .fromAccount("CUSTODIAN-CASH")
        .toAccount("BORROWER-CASH")
        .amount(new BigDecimal("100.00"))
        .currency("INR")
        .status(SettlementInstructionStatus.PENDING)
        .build();
    collateral = Collateral.builder()
        .collateralId(60L)
        .contractId(20L)
        .collateralType(com.hexaware.custodian_service.enums.CollateralType.CASH)
        .currency("INR")
        .postedCashAmount(new BigDecimal("100.00"))
        .releasedCashAmount(BigDecimal.ZERO)
        .status("POSTED")
        .build();
    when(access.actor(authentication, true)).thenReturn(custodian());
    when(contracts.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(contract));
    when(requests.findById(30L)).thenReturn(Optional.of(
        LoanRequest.builder().requestId(30L).requestReference("REQ-001").mslaId(40L)
            .settlementCurrency("INR").build()));
    when(agreements.findById(40L)).thenReturn(Optional.of(
        MslaAgreement.builder().mslaId(40L).mandateId(50L).borrowerOrgId(2L)
            .agreementStatus(AgreementStatus.ACTIVE).build()));
    when(mandates.findById(50L)).thenReturn(Optional.of(
        LenderMandate.builder().mandateId(50L).custodianOrgId(1L)
            .status(MandateStatus.ACTIVE).build()));
    when(organizations.findById(1L)).thenReturn(Optional.of(custodian()));
    when(organizations.findById(2L)).thenReturn(Optional.of(borrower()));
  }

  @Test
  void confirmsPayoutAndReleasesOnlyConfirmedCash() {
    when(settlements.findLockedByInstructionReference(INSTRUCTION_REFERENCE))
        .thenReturn(Optional.of(instruction));
    when(settlements.findByExternalConfirmationReference(CONFIRMATION_REFERENCE))
        .thenReturn(Optional.empty());
    when(collaterals.findLockedByContractId(20L)).thenReturn(List.of(collateral));
    when(settlements.findByContractId(20L)).thenReturn(List.of(instruction));
    when(settlements.saveAndFlush(instruction)).thenAnswer(invocation -> {
      instruction.setVersion(1L);
      return instruction;
    });

    SettlementInstruction confirmed = service.confirm(
        authentication, TRANSACTION_REFERENCE, INSTRUCTION_REFERENCE, CONFIRMATION_REFERENCE);

    assertThat(confirmed.getStatus()).isEqualTo(SettlementInstructionStatus.SETTLED);
    assertThat(collateral.getReleasedCashAmount()).isEqualByComparingTo("100.00");
    assertThat(collateral.getStatus()).isEqualTo("RELEASED");
    verify(outgoing).enqueue(anyString(), any(EventEnvelope.class));
  }

  @Test
  void rejectsPayoutForAnotherContractWithoutChangingCollateral() {
    instruction.setContractId(21L);
    when(settlements.findLockedByInstructionReference(INSTRUCTION_REFERENCE))
        .thenReturn(Optional.of(instruction));

    assertThatThrownBy(() -> service.confirm(
        authentication, TRANSACTION_REFERENCE, INSTRUCTION_REFERENCE, CONFIRMATION_REFERENCE))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("not a payout for this contract");
    assertThat(collateral.getReleasedCashAmount()).isZero();
    verify(outgoing, never()).enqueue(anyString(), any(EventEnvelope.class));
  }

  @Test
  void closesContractAfterFinalChecksAndPublishesClosure() {
    contract.setUsedCreditAmount(BigDecimal.ZERO);
    contract.setReservedCreditAmount(BigDecimal.ZERO);
    SettlementInstruction delivery = SettlementInstruction.builder()
        .instructionType("SECURITY_DELIVERY")
        .quantity(new BigDecimal("100"))
        .settledAt(Instant.parse("2026-11-08T09:00:00Z"))
        .build();
    SettlementInstruction returned = SettlementInstruction.builder()
        .instructionType("SECURITY_RETURN")
        .quantity(new BigDecimal("100"))
        .settledAt(Instant.parse("2026-11-08T11:00:00Z"))
        .build();
    when(settlements.findByContractIdAndStatusOrderBySettledAtAsc(
        20L, SettlementInstructionStatus.SETTLED))
        .thenReturn(List.of(delivery, returned));
    when(accruals.accrue(authentication, TRANSACTION_REFERENCE,
        java.time.LocalDate.parse("2026-11-08"),
        java.time.LocalDate.parse("2026-11-08"), null))
        .thenReturn(Map.of("postedCount", 0, "pending", List.of()));
    when(accruals.charges(authentication, TRANSACTION_REFERENCE)).thenReturn(List.of());
    when(feePayments.findLockedByContractId(20L)).thenReturn(List.of());
    when(recalls.findByContractIdOrderByRequestedAtDesc(20L)).thenReturn(List.of());
    when(marginCalls.findByContractIdOrderByCreatedAtDesc(20L)).thenReturn(List.of());
    when(settlements.findByContractId(20L)).thenReturn(List.of(delivery, returned));
    when(collaterals.findLockedByContractId(20L)).thenReturn(List.of());
    when(access.user(authentication)).thenReturn(user);
    when(user.getUserId()).thenReturn(7L);
    doAnswer(invocation -> {
      contract.setVersion(contract.getVersion() + 1);
      return contract;
    }).when(contracts).saveAndFlush(contract);

    Map<String, Object> result = service.close(
        authentication, TRANSACTION_REFERENCE, "CLOSE-002", "all final obligations settled");

    assertThat(result.get("status")).isEqualTo("CLOSED");
    assertThat(contract.getContractStatus()).isEqualTo(LoanContractStatus.CLOSED);
    verify(histories).save(any(ContractStatusHistory.class));
    verify(outgoing).enqueue(anyString(), any(EventEnvelope.class));
  }

  @Test
  void duplicatePayoutConfirmationDoesNotApplyBalanceTwice() {
    instruction.setStatus(SettlementInstructionStatus.SETTLED);
    instruction.setExternalConfirmationReference(CONFIRMATION_REFERENCE);
    when(settlements.findLockedByInstructionReference(INSTRUCTION_REFERENCE))
        .thenReturn(Optional.of(instruction));

    SettlementInstruction duplicate = service.confirm(
        authentication, TRANSACTION_REFERENCE, INSTRUCTION_REFERENCE, CONFIRMATION_REFERENCE);

    assertThat(duplicate).isSameAs(instruction);
    assertThat(collateral.getReleasedCashAmount()).isZero();
    verify(collaterals, never()).saveAndFlush(any(Collateral.class));
    verify(outgoing, never()).enqueue(anyString(), any(EventEnvelope.class));
  }

  @Test
  void duplicateClosureWithSameReferenceIsIdempotent() {
    contract.setContractStatus(LoanContractStatus.CLOSED);
    String closureReference = "CLOSE-001";
    String eventId = UUID.nameUUIDFromBytes(
        ("CONTRACT_CLOSED:" + TRANSACTION_REFERENCE + ":" + closureReference)
            .getBytes(StandardCharsets.UTF_8)).toString();
    when(histories.findByContractIdAndSourceEventId(20L, eventId))
        .thenReturn(Optional.of(ContractStatusHistory.builder().reason("final payout confirmed").build()));

    Map<String, Object> result = service.close(
        authentication, TRANSACTION_REFERENCE, closureReference, "final payout confirmed");

    assertThat(result.get("status")).isEqualTo("CLOSED");
    assertThat(result.get("idempotent")).isEqualTo(true);
    verify(contracts, never()).saveAndFlush(any(LoanContract.class));
    verify(outgoing, never()).enqueue(anyString(), any(EventEnvelope.class));
  }

  private Organization custodian() {
    return Organization.builder()
        .organizationId(1L)
        .organizationReference("CUSTODIAN-001")
        .status(OrganizationStatus.ACTIVE)
        .build();
  }

  private Organization borrower() {
    return Organization.builder()
        .organizationId(2L)
        .organizationReference("BORROWER-001")
        .status(OrganizationStatus.ACTIVE)
        .build();
  }
}
