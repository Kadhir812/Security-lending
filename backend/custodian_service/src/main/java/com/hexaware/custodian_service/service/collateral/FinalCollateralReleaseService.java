package com.hexaware.custodian_service.service.collateral;

import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.InboxMessage;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.InboxMessageStatus;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.config.MessagingProperties;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.InboxMessageRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinalCollateralReleaseService {
  private static final String REQUEST_EVENT = "FINAL_COLLATERAL_RELEASE_REQUESTED";

  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final SettlementInstructionRepository settlements;
  private final CollateralRepository collaterals;
  private final InboxMessageRepository inbox;
  private final MessagingProperties messaging;
  private final FinalCollateralReleaseEvents events;
  private final ContractClosureService closure;
  private final Clock clock;

  @Transactional
  public void receiveRequest(EventEnvelope event) {
    Request request = validateRequestEnvelope(event);
    LoanContract contract = lockedContract(request.transactionReference());
    FinalReleaseParties parties = parties(contract);
    if (!request.borrowerReference().equals(parties.borrowerReference())
        || !request.custodianReference().equals(parties.custodianReference())
        || !request.requestReference().equals(
            requests.findById(contract.getLoanRequestId()).orElseThrow().getRequestReference())) {
      throw new InvalidEnvelopeException("FINAL_RELEASE_PARTIES_MISMATCH");
    }
    if (contract.getContractStatus() != LoanContractStatus.RETURNED
        || FinalCollateralReleaseRules.nvl(contract.getOutstandingQuantity()) != 0
        || FinalCollateralReleaseRules.nvl(contract.getDeliveredQuantity()) == 0
        || !Objects.equals(contract.getDeliveredQuantity(), contract.getReturnedQuantity())) {
      throw new InvalidEnvelopeException("FINAL_RELEASE_CONTRACT_NOT_RETURNED");
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> pendingRequests(Authentication authentication) {
    Organization actor = access.actor(authentication, false);
    return inbox.findByConsumerNameAndEventTypeAndStatusOrderByReceivedAtDesc(
            messaging.getConsumerName(), REQUEST_EVENT, InboxMessageStatus.PROCESSED)
        .stream()
        .filter(row -> Objects.equals(
            actor.getOrganizationReference(),
            row.getPayload().get("custodianOrganizationReference")))
        .map(row -> {
          Map<String, Object> request = new LinkedHashMap<>();
          request.put("transactionReference", row.getPayload().get("transactionReference"));
          request.put("releaseReference", row.getPayload().get("releaseReference"));
          request.put("borrowerOrganizationReference",
              row.getPayload().get("borrowerOrganizationReference"));
          request.put("receivedAt", row.getReceivedAt());
          request.put("status", releaseRequestStatus(
              String.valueOf(row.getPayload().get("transactionReference")),
              String.valueOf(row.getPayload().get("releaseReference"))));
          return request;
        })
        .toList();
  }

  @Transactional
  public Map<String, Object> approve(
      Authentication authentication, String transactionReference, String releaseReference) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FinalReleaseParties parties = parties(contract);
    requireCustodian(actor, parties);
    InboxMessage requestMessage = approvedRequest(contract, parties, releaseReference);
    List<SettlementInstruction> priorPayouts =
        settlements.findByContractId(contract.getContractId()).stream()
            .filter(row -> "COLLATERAL_RELEASE".equals(row.getInstructionType()))
            .filter(
                row ->
                    releaseReference.equals(
                        FinalCollateralReleaseRules.releaseReference(
                            row.getInstructionReference())))
            .toList();
    if (!priorPayouts.isEmpty()
        && priorPayouts.stream().allMatch(
            row -> row.getStatus() == SettlementInstructionStatus.SETTLED)
        && totalHeldCash(contract).signum() == 0) {
      closure.assertNoHeldCollateral(contract);
      Map<String, Object> completed = new LinkedHashMap<>();
      completed.put("transactionReference", transactionReference);
      completed.put("releaseReference", releaseReference);
      completed.put("releaseStatus", "SETTLED");
      completed.put("instructions", priorPayouts.stream()
          .map(row -> releaseResult(row, releaseReference)).toList());
      completed.put("requestEventId", requestMessage.getEventId());
      completed.put("idempotent", true);
      return completed;
    }
    if (contract.getContractStatus() == LoanContractStatus.CLOSED) {
      throw SetupException.conflict("Closed contracts reject new release activity");
    }

    FinalCollateralReleaseRules.validateReturnedContract(contract);
    Map<String, Object> accrual = closure.completeFinalAccrual(authentication, contract);
    closure.assertFinalFeesSettled(authentication, contract);
    closure.assertNoActiveCallsOrUnresolvedSettlements(contract, releaseReference);

    List<Collateral> balances = collaterals.findLockedByContractId(contract.getContractId());
    List<Map<String, Object>> results = new ArrayList<>();
    int payoutCount = 0;
    for (Collateral collateral : balances) {
      if (collateral.getCollateralType().name().equals("CASH")) {
        if ("FAILED".equals(collateral.getStatus())) continue;
        FinalCollateralReleaseRules.validateCashCollateral(contract, collateral);
        BigDecimal held = collateral.getPostedCashAmount().subtract(collateral.getReleasedCashAmount());
        if (held.signum() <= 0) continue;
        payoutCount++;
      } else if (collateral.getPledgedQuantity() != null
          && collateral.getPledgedQuantity().compareTo(
              FinalCollateralReleaseRules.nvl(collateral.getReleasedQuantity())) > 0) {
        throw SetupException.conflict("Non-CASH collateral remains held; final CASH release is not applicable");
      }
    }

    for (Collateral collateral : balances) {
      if (!"CASH".equals(collateral.getCollateralType().name())) continue;
      if ("FAILED".equals(collateral.getStatus())) continue;
      BigDecimal amount = collateral.getPostedCashAmount().subtract(collateral.getReleasedCashAmount());
      if (amount.signum() <= 0) continue;
      String instructionReference = instructionReference(releaseReference, collateral.getCollateralId());
      SettlementInstruction instruction = settlements.findByInstructionReference(instructionReference)
          .orElse(null);
      if (instruction == null) {
        instruction = SettlementInstruction.builder()
            .instructionReference(instructionReference)
            .contractId(contract.getContractId())
            .collateralId(collateral.getCollateralId())
            .instructionType("COLLATERAL_RELEASE")
            .fromOrganizationId(parties.custodianId())
            .toOrganizationId(parties.borrowerId())
            .fromAccount("CUSTODIAN-CASH")
            .toAccount("BORROWER-CASH")
            .amount(amount)
            .currency(contract.getSettlementCurrency())
            .status(SettlementInstructionStatus.PENDING)
            .settlementDate(LocalDate.now(clock.withZone(ZoneOffset.UTC)))
            .createdAt(clock.instant())
            .build();
        settlements.saveAndFlush(instruction);
        events.publishReleaseUpdate(
            contract,
            instruction,
            parties,
            releaseReference,
            Math.max(payoutCount, instructionCount(contract, releaseReference)),
            "PENDING",
            instruction.getVersion() + 1,
            false);
      } else {
        FinalCollateralReleaseRules.validateReleaseInstruction(instruction, contract, collateral, parties, amount);
        if (instruction.getStatus() == SettlementInstructionStatus.FAILED) {
          instruction.setStatus(SettlementInstructionStatus.PENDING);
          instruction.setFailureReason(null);
          settlements.saveAndFlush(instruction);
          events.publishReleaseUpdate(
              contract,
              instruction,
              parties,
              releaseReference,
              Math.max(payoutCount, instructionCount(contract, releaseReference)),
              "PENDING",
              instruction.getVersion() + 1,
              false);
        }
      }
      results.add(releaseResult(instruction, releaseReference));
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("transactionReference", transactionReference);
    result.put("releaseReference", releaseReference);
    result.put("releaseStatus", results.isEmpty() ? "NO_HELD_CASH" : summarize(results));
    result.put("finalAccrual", accrual);
    result.put("instructions", results);
    result.put("requestEventId", requestMessage.getEventId());
    return result;
  }

  @Transactional
  public SettlementInstruction confirm(
      Authentication authentication,
      String transactionReference,
      String instructionReference,
      String confirmationReference) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FinalReleaseParties parties = parties(contract);
    requireCustodian(actor, parties);
    SettlementInstruction instruction = releaseInstruction(instructionReference, contract);
    if (instruction.getStatus() == SettlementInstructionStatus.SETTLED) {
      if (Objects.equals(instruction.getExternalConfirmationReference(), confirmationReference)) {
        return instruction;
      }
      throw SetupException.conflict("Collateral payout already has a different confirmation");
    }
    ensureContractNotClosed(contract);
    FinalCollateralReleaseRules.validateReturnedContract(contract);
    if (instruction.getStatus() != SettlementInstructionStatus.PENDING
        && instruction.getStatus() != SettlementInstructionStatus.PROCESSING
        && instruction.getStatus() != SettlementInstructionStatus.FAILED) {
      throw SetupException.conflict("Collateral payout is not confirmable");
    }
    if (!FinalCollateralReleaseRules.validConfirmationReference(confirmationReference)) {
      throw SetupException.invalid("Invalid payout confirmation reference");
    }
    settlements.findByExternalConfirmationReference(confirmationReference).ifPresent(existing -> {
      if (!existing.getInstructionId().equals(instruction.getInstructionId())) {
        throw SetupException.conflict("Confirmation reference was used for another instruction");
      }
    });
    Collateral collateral = lockedCollateral(contract, instruction);
    FinalCollateralReleaseRules.validateReleaseInstruction(
        instruction, contract, collateral, parties, instruction.getAmount());
    BigDecimal released = FinalCollateralReleaseRules.nvl(collateral.getReleasedCashAmount()).add(instruction.getAmount());
    if (released.compareTo(collateral.getPostedCashAmount()) > 0) {
      throw SetupException.conflict("Payout would release more CASH than confirmed collateral");
    }
    instruction.setExternalConfirmationReference(confirmationReference);
    instruction.setFailureReason(null);
    instruction.setStatus(SettlementInstructionStatus.SETTLED);
    instruction.setSettledAt(clock.instant());
    collateral.setReleasedCashAmount(released);
    collateral.setStatus(released.compareTo(collateral.getPostedCashAmount()) == 0
        ? "RELEASED" : "PARTIALLY_RELEASED");
    settlements.saveAndFlush(instruction);
    collaterals.saveAndFlush(collateral);
    String releaseReference =
        FinalCollateralReleaseRules.releaseReference(instruction.getInstructionReference());
    events.publishReleaseUpdate(
        contract,
        instruction,
        parties,
        releaseReference,
        instructionCount(contract, releaseReference),
        "SETTLED",
        instruction.getVersion() + 1,
        true);
    return instruction;
  }

  @Transactional
  public SettlementInstruction fail(
      Authentication authentication,
      String transactionReference,
      String instructionReference,
      String failureReason) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FinalReleaseParties parties = parties(contract);
    requireCustodian(actor, parties);
    SettlementInstruction instruction = releaseInstruction(instructionReference, contract);
    if (instruction.getStatus() == SettlementInstructionStatus.FAILED
        && Objects.equals(instruction.getFailureReason(), failureReason)) return instruction;
    ensureContractNotClosed(contract);
    FinalCollateralReleaseRules.validateReturnedContract(contract);
    if (instruction.getStatus() != SettlementInstructionStatus.PENDING
        && instruction.getStatus() != SettlementInstructionStatus.PROCESSING
        && instruction.getStatus() != SettlementInstructionStatus.FAILED) {
      throw SetupException.conflict("Only a pending collateral payout can be failed");
    }
    if (failureReason == null || failureReason.isBlank() || failureReason.length() > 500) {
      throw SetupException.invalid("A payout failure reason of 1 to 500 characters is required");
    }
    instruction.setStatus(SettlementInstructionStatus.FAILED);
    instruction.setFailureReason(failureReason);
    settlements.saveAndFlush(instruction);
    String releaseReference = FinalCollateralReleaseRules.releaseReference(instruction.getInstructionReference());
    events.publishReleaseUpdate(
        contract,
        instruction,
        parties,
        releaseReference,
        instructionCount(contract, releaseReference),
        "FAILED",
        instruction.getVersion() + 1,
        false);
    return instruction;
  }

  @Transactional
  public Map<String, Object> close(
      Authentication authentication,
      String transactionReference,
      String closureReference,
      String reason) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FinalReleaseParties parties = parties(contract);
    requireCustodian(actor, parties);
    return closure.close(authentication, contract, parties, closureReference, reason);
  }

  private BigDecimal totalHeldCash(LoanContract contract) {
    return collaterals.findByContractId(contract.getContractId()).stream()
        .filter(row -> "CASH".equals(row.getCollateralType().name()))
        .filter(row -> !"FAILED".equals(row.getStatus()))
        .map(
            row ->
                FinalCollateralReleaseRules.nvl(row.getPostedCashAmount())
                    .subtract(FinalCollateralReleaseRules.nvl(row.getReleasedCashAmount())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private SettlementInstruction releaseInstruction(String reference, LoanContract contract) {
    SettlementInstruction instruction = settlements.findLockedByInstructionReference(reference)
        .orElseThrow(() -> SetupException.missing("Collateral release instruction not found"));
    if (!instruction.getContractId().equals(contract.getContractId())
        || !"COLLATERAL_RELEASE".equals(instruction.getInstructionType())) {
      throw SetupException.conflict("Instruction is not a payout for this contract");
    }
    return instruction;
  }

  private Collateral lockedCollateral(LoanContract contract, SettlementInstruction instruction) {
    return collaterals.findLockedByContractId(contract.getContractId()).stream()
        .filter(row -> Objects.equals(row.getCollateralId(), instruction.getCollateralId()))
        .findFirst()
        .orElseThrow(() -> SetupException.conflict("Payout collateral balance is unavailable"));
  }

  private InboxMessage approvedRequest(
      LoanContract contract, FinalReleaseParties parties, String releaseReference) {
    if (!FinalCollateralReleaseRules.validReleaseReference(releaseReference)) {
      throw SetupException.invalid("Invalid final release reference");
    }
    String eventId = requestEventId(contract.getTransactionReference(), releaseReference);
    InboxMessage message =
        inbox.findByConsumerNameAndEventId(messaging.getConsumerName(), eventId)
            .orElseThrow(
                () -> SetupException.missing("Borrower final release request was not received"));
    Map<String, Object> payload = message.getPayload();
    if (message.getStatus() != InboxMessageStatus.PROCESSED
        || !REQUEST_EVENT.equals(message.getEventType())
        || !Objects.equals(payload.get("transactionReference"), contract.getTransactionReference())
        || !Objects.equals(payload.get("releaseReference"), releaseReference)
        || !Objects.equals(payload.get("borrowerOrganizationReference"), parties.borrowerReference())
        || !Objects.equals(payload.get("custodianOrganizationReference"), parties.custodianReference())) {
      throw SetupException.conflict("Received final release request does not match the contract");
    }
    return message;
  }

  private Request validateRequestEnvelope(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference =
        FinalCollateralReleaseRules.text(payload.get("transactionReference"));
    String releaseReference = FinalCollateralReleaseRules.text(payload.get("releaseReference"));
    String borrowerReference =
        FinalCollateralReleaseRules.text(payload.get("borrowerOrganizationReference"));
    String custodianReference =
        FinalCollateralReleaseRules.text(payload.get("custodianOrganizationReference"));
    String requestReference = FinalCollateralReleaseRules.text(payload.get("requestReference"));
    if (!REQUEST_EVENT.equals(event.getEventType())
        || !"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || FinalCollateralReleaseRules.number(payload.get("payloadVersion")) != 1
        || FinalCollateralReleaseRules.number(event.getAggregateVersion()) != 0
        || !Objects.equals(event.getAggregateReference(), releaseReference)
        || !Objects.equals(event.getCorrelationId(), releaseReference)
        || !Objects.equals(event.getSenderOrganizationReference(), borrowerReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), custodianReference)
        || !Objects.equals(event.getEventId(), requestEventId(transactionReference, releaseReference))
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || !FinalCollateralReleaseRules.validReference(transactionReference)
        || !FinalCollateralReleaseRules.validReleaseReference(releaseReference)
        || !FinalCollateralReleaseRules.validReference(borrowerReference)
        || !FinalCollateralReleaseRules.validReference(custodianReference)
        || !FinalCollateralReleaseRules.validReference(requestReference)) {
      throw new InvalidEnvelopeException("FINAL_RELEASE_REQUEST_ENVELOPE_MISMATCH");
    }
    return new Request(
        transactionReference, releaseReference, borrowerReference, custodianReference, requestReference);
  }

  private FinalReleaseParties parties(LoanContract contract) {
    LoanRequest request =
        requests
            .findById(contract.getLoanRequestId())
            .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement =
        agreements
            .findById(request.getMslaId())
            .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate =
        mandates
            .findById(agreement.getMandateId())
            .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    Organization borrower =
        organizations
            .findById(agreement.getBorrowerOrgId())
            .orElseThrow(() -> SetupException.missing("Borrower organization not found"));
    Organization custodian =
        organizations
            .findById(mandate.getCustodianOrgId())
            .orElseThrow(() -> SetupException.missing("Custodian organization not found"));
    if (agreement.getAgreementStatus() != AgreementStatus.ACTIVE
        || mandate.getStatus() != MandateStatus.ACTIVE
        || borrower.getStatus() != OrganizationStatus.ACTIVE
        || custodian.getStatus() != OrganizationStatus.ACTIVE
        || !contract.getSettlementCurrency().equals(request.getSettlementCurrency())) {
      throw SetupException.conflict("Contract parties or agreement are no longer active");
    }
    return new FinalReleaseParties(
        borrower.getOrganizationId(),
        borrower.getOrganizationReference(),
        custodian.getOrganizationId(),
        custodian.getOrganizationReference());
  }

  private void requireCustodian(Organization actor, FinalReleaseParties parties) {
    if (!actor.getOrganizationId().equals(parties.custodianId())) {
      throw new AccessDeniedException("Contract is outside the authenticated Custodian mandate");
    }
  }

  private int instructionCount(LoanContract contract, String releaseReference) {
    if (releaseReference == null) return 0;
    return (int)
        settlements.findByContractId(contract.getContractId()).stream()
            .filter(row -> "COLLATERAL_RELEASE".equals(row.getInstructionType()))
            .filter(
                row ->
                    releaseReference.equals(
                        FinalCollateralReleaseRules.releaseReference(
                            row.getInstructionReference())))
            .count();
  }

  private String releaseRequestStatus(String transactionReference, String releaseReference) {
    LoanContract contract = contracts.findByTransactionReference(transactionReference).orElse(null);
    if (contract == null) return "CONTRACT_MISSING";
    if (contract.getContractStatus() == LoanContractStatus.CLOSED) return "CLOSED";
    List<SettlementInstruction> instructions =
        settlements.findByContractId(contract.getContractId()).stream()
            .filter(row -> "COLLATERAL_RELEASE".equals(row.getInstructionType()))
            .filter(
                row ->
                    releaseReference.equals(
                        FinalCollateralReleaseRules.releaseReference(
                            row.getInstructionReference())))
            .toList();
    if (instructions.isEmpty()) return "PENDING_APPROVAL";
    if (instructions.stream().anyMatch(
        row -> row.getStatus() == SettlementInstructionStatus.PENDING
            || row.getStatus() == SettlementInstructionStatus.PROCESSING)) return "PAYOUT_PENDING";
    if (instructions.stream().anyMatch(row -> row.getStatus() == SettlementInstructionStatus.FAILED)) {
      return "PAYOUT_FAILED";
    }
    return instructions.stream().allMatch(
        row -> row.getStatus() == SettlementInstructionStatus.SETTLED)
        ? "SETTLED" : "UNKNOWN";
  }

  private Map<String, Object> releaseResult(
      SettlementInstruction instruction, String releaseReference) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("instructionReference", instruction.getInstructionReference());
    result.put("releaseReference", releaseReference);
    result.put("amount", instruction.getAmount());
    result.put("currency", instruction.getCurrency());
    result.put("status", instruction.getStatus().name());
    result.put("settledAt", instruction.getSettledAt());
    result.put("failureReason", instruction.getFailureReason());
    return result;
  }

  private String summarize(List<Map<String, Object>> results) {
    if (results.stream().allMatch(row -> "SETTLED".equals(row.get("status")))) return "SETTLED";
    if (results.stream().anyMatch(row -> "PENDING".equals(row.get("status"))
        || "PROCESSING".equals(row.get("status")))) return "PENDING";
    return "FAILED";
  }

  private String instructionReference(String releaseReference, Long collateralId) {
    return "R:" + releaseReference + ":" + collateralId;
  }

  private String requestEventId(String transactionReference, String releaseReference) {
    if (transactionReference == null || releaseReference == null) return null;
    return UUID.nameUUIDFromBytes(
        (REQUEST_EVENT + ":" + transactionReference + ":" + releaseReference)
            .getBytes(StandardCharsets.UTF_8)).toString();
  }

  private LoanContract lockedContract(String reference) {
    return contracts.findLockedByTransactionReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private void ensureContractNotClosed(LoanContract contract) {
    if (contract.getContractStatus() == LoanContractStatus.CLOSED) {
      throw SetupException.conflict("Closed contracts reject new settlement activity");
    }
  }

  private record Request(
      String transactionReference,
      String releaseReference,
      String borrowerReference,
      String custodianReference,
      String requestReference) {}
}
