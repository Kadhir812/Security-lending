package com.hexaware.custodian_service.service.returns;

import com.hexaware.custodian_service.client.MtmCalculationResponse;
import com.hexaware.custodian_service.dto.request.CreateRecallRequest;
import com.hexaware.custodian_service.dto.request.SimulatedSettlementConfirmationRequest;
import com.hexaware.custodian_service.dto.request.SimulatedSettlementFailureRequest;
import com.hexaware.custodian_service.entity.*;
import com.hexaware.custodian_service.enums.*;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.*;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.margin.MarginCallService;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SecuritiesReturnService {
  private static final List<SettlementInstructionStatus> PENDING_STATUSES =
      List.of(SettlementInstructionStatus.PENDING, SettlementInstructionStatus.PROCESSING);
  private static final List<RecallStatus> ACTIVE_RECALL_STATUSES =
      List.of(RecallStatus.OPEN, RecallStatus.PROCESSING, RecallStatus.PARTIALLY_RETURNED,
          RecallStatus.OVERDUE, RecallStatus.DEFAULTED);

  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final RecallRequestRepository recalls;
  private final SettlementInstructionRepository settlements;
  private final LenderInventoryRepository inventories;
  private final InventoryMovementRepository movements;
  private final OutgoingEventService outgoing;
  private final MarginCallService marginCalls;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;

  @Transactional
  public Map<String, Object> createRecall(
      Authentication authentication, String transactionReference, CreateRecallRequest request) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    Parties parties = parties(contract);
    requireCustodian(actor, parties);
    validateLoanOutstanding(contract);
    Instant now = clock.instant();
    if (request.dueAt() == null || !request.dueAt().isAfter(now)) {
      throw SetupException.invalid("Recall dueAt must be in the future");
    }

    Optional<RecallRequest> existing = recalls.findByRecallReference(request.recallReference());
    if (existing.isPresent()) {
      RecallRequest recall = existing.get();
      if (recall.getContractId().equals(contract.getContractId())
          && recall.getRequestedQuantity().equals(request.quantity())
          && recall.getDueAt().equals(request.dueAt())
          && recall.getRecallReason().equals(request.reason())) {
        return recallView(recall, contract);
      }
      throw SetupException.conflict("Recall reference was reused with different terms");
    }

    long available = availableToRecall(contract);
    if (request.quantity() > available) {
      throw SetupException.conflict("Recall quantity exceeds uncommitted outstanding quantity");
    }
    RecallRequest recall = recalls.save(RecallRequest.builder()
        .recallReference(request.recallReference())
        .contractId(contract.getContractId())
        .requestedQuantity(request.quantity())
        .returnedQuantity(0L)
        .requestedBy(access.user(authentication).getUserId())
        .requestedAt(now)
        .dueAt(request.dueAt())
        .recallReason(request.reason())
        .recallStatus(RecallStatus.OPEN)
        .build());
    publishRecall(recall, contract, parties, "RECALL_UPDATED");
    return recallView(recall, contract);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listRecalls(
      Authentication authentication, String transactionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    requireCustodian(actor, parties(contract));
    return recalls.findByContractIdOrderByRequestedAtDesc(contract.getContractId()).stream()
        .map(recall -> recallView(recall, contract))
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getRecall(
      Authentication authentication, String transactionReference, String recallReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    requireCustodian(actor, parties(contract));
    RecallRequest recall = recalls.findByRecallReference(recallReference)
        .filter(row -> row.getContractId().equals(contract.getContractId()))
        .orElseThrow(() -> SetupException.missing("Recall not found"));
    return recallView(recall, contract);
  }

  @Transactional
  public void receiveReturnInstruction(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String instructionReference = text(payload.get("instructionReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    String recallReference = text(payload.get("recallReference"));
    long quantity = wholeQuantity(payload.get("quantity"));
    if (!"SECURITY_RETURN_SUBMITTED".equals(event.getEventType())
        || !"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || number(payload.get("payloadVersion")) != 1
        || !Objects.equals(event.getAggregateReference(), instructionReference)
        || !Objects.equals(event.getCorrelationId(), instructionReference)
        || !Objects.equals(event.getAggregateVersion(), 0L)
        || !Objects.equals(event.getSenderOrganizationReference(), borrowerReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), custodianReference)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || !validReference(instructionReference)
        || transactionReference == null
        || borrowerReference == null
        || custodianReference == null
        || (payload.get("recallReference") != null
            && (recallReference == null || !validReference(recallReference)))
        || quantity <= 0) {
      throw new com.hexaware.custodian_service.exception.InvalidEnvelopeException(
          "SECURITY_RETURN_ENVELOPE_MISMATCH");
    }

    LoanContract contract = lockedContract(transactionReference);
    validateLoanOutstanding(contract);
    LoanRequest loanRequest = requestFor(contract);
    Parties parties = parties(contract);
    if (!borrowerReference.equals(parties.borrowerReference())
        || !custodianReference.equals(parties.custodianReference())
        || !loanRequest.getSecurityIsin().equals(payload.get("securityIsin"))
        || !Objects.equals(contract.getSettlementCurrency(), payload.get("currency"))) {
      throw new com.hexaware.custodian_service.exception.InvalidEnvelopeException(
          "SECURITY_RETURN_PARTIES_OR_SECURITY_MISMATCH");
    }

    Optional<SettlementInstruction> existing =
        settlements.findByInstructionReference(instructionReference);
    if (existing.isPresent()) {
      if (sameReturn(existing.get(), contract, parties, quantity, recallReference)) return;
      throw SetupException.conflict("Return instruction reference was reused");
    }
    if (quantity > availableReturnQuantity(contract, null)) {
      throw SetupException.conflict("Return quantity exceeds outstanding quantity after pending returns");
    }
    RecallRequest recall = recallReference == null ? null : lockedRecall(recallReference, contract);
    if (recall != null && quantity > availableRecallReturnQuantity(recall, null)) {
      throw SetupException.conflict("Return quantity exceeds the recall's unreturned quantity");
    }

    Instant now = clock.instant();
    SettlementInstruction instruction = SettlementInstruction.builder()
        .instructionReference(instructionReference)
        .contractId(contract.getContractId())
        .recallId(recall == null ? null : recall.getRecallId())
        .instructionType("SECURITY_RETURN")
        .fromOrganizationId(parties.borrowerId())
        .toOrganizationId(parties.lenderId())
        .fromAccount("BORROWER-SECURITIES")
        .toAccount("LENDER-SECURITIES")
        .securityIsin(loanRequest.getSecurityIsin())
        .quantity(BigDecimal.valueOf(quantity))
        .currency(contract.getSettlementCurrency())
        .status(SettlementInstructionStatus.PENDING)
        .settlementDate(now.atZone(ZoneOffset.UTC).toLocalDate())
        .createdAt(now)
        .build();
    settlements.save(instruction);
    publishReturnUpdate(instruction, contract, parties, recallReference, "PENDING",
        instruction.getVersion() + 1, false);
  }

  public SettlementInstruction confirmReturn(
      Authentication authentication,
      String transactionReference,
      SimulatedSettlementConfirmationRequest confirmation) {
    Organization actor = access.actor(authentication, false);
    LoanContract initial = contract(transactionReference);
    Parties initialParties = parties(initial);
    requireCustodian(actor, initialParties);
    SettlementInstruction initialInstruction = settlements.findByInstructionReference(
            confirmation.instructionReference())
        .orElseThrow(() -> SetupException.missing("Return instruction not found"));
    validateInstructionContract(initialInstruction, initial);
    validateInstructionParties(initialInstruction, initialParties, requestFor(initial));
    if (initialInstruction.getStatus() == SettlementInstructionStatus.SETTLED) {
      if (Objects.equals(initialInstruction.getExternalConfirmationReference(),
          confirmation.confirmationReference())) return initialInstruction;
      throw SetupException.conflict("Return instruction has a conflicting confirmation");
    }
    validateLoanOutstanding(initial);
    if (initialInstruction.getStatus() != SettlementInstructionStatus.PENDING
        && initialInstruction.getStatus() != SettlementInstructionStatus.PROCESSING
        && initialInstruction.getStatus() != SettlementInstructionStatus.FAILED) {
      throw SetupException.conflict("Return instruction is not confirmable");
    }
    long quantity = wholeQuantity(initialInstruction.getQuantity());
    long remainingQuantity = initial.getOutstandingQuantity() - quantity;
    if (remainingQuantity < 0) {
      throw SetupException.conflict("Return quantity exceeds current outstanding quantity");
    }
    String calculationReference = UUID.nameUUIDFromBytes(
        ("RETURN-MTM:" + initialInstruction.getInstructionReference())
            .getBytes(StandardCharsets.UTF_8)).toString();
    MtmCalculationResponse mtmResult =
        marginCalls.calculateReturnValuation(initial, remainingQuantity, calculationReference);

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    try {
      return transaction.execute(status -> confirmReturnTransaction(
          authentication, transactionReference, confirmation, mtmResult, quantity,
          initial.getVersion(), initial.getOutstandingQuantity()));
    } catch (DataIntegrityViolationException duplicate) {
      SettlementInstruction settled = settlements.findByInstructionReference(
              confirmation.instructionReference())
          .orElseThrow(() -> SetupException.conflict("Concurrent return confirmation conflicted"));
      if (settled.getStatus() == SettlementInstructionStatus.SETTLED
          && Objects.equals(settled.getExternalConfirmationReference(),
              confirmation.confirmationReference())) {
        return settled;
      }
      throw SetupException.conflict("Concurrent return confirmation conflicted");
    }
  }

  @Transactional
  public SettlementInstruction failReturn(
      Authentication authentication,
      String transactionReference,
      SimulatedSettlementFailureRequest failure) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    Parties parties = parties(contract);
    requireCustodian(actor, parties);
    SettlementInstruction instruction = settlements.findLockedByInstructionReference(
            failure.instructionReference())
        .orElseThrow(() -> SetupException.missing("Return instruction not found"));
    validateInstructionContract(instruction, contract);
    if (instruction.getStatus() == SettlementInstructionStatus.FAILED
        && Objects.equals(instruction.getFailureReason(), failure.failureReason())) {
      return instruction;
    }
    if (instruction.getStatus() != SettlementInstructionStatus.PENDING
        && instruction.getStatus() != SettlementInstructionStatus.PROCESSING
        && instruction.getStatus() != SettlementInstructionStatus.FAILED) {
      throw SetupException.conflict("Only a pending return instruction can be failed");
    }
    instruction.setStatus(SettlementInstructionStatus.FAILED);
    instruction.setFailureReason(failure.failureReason());
    settlements.saveAndFlush(instruction);
    publishReturnUpdate(instruction, contract, parties, recallReference(instruction),
        "FAILED", instruction.getVersion() + 1, false);
    return instruction;
  }

  private SettlementInstruction confirmReturnTransaction(
      Authentication authentication,
      String transactionReference,
      SimulatedSettlementConfirmationRequest confirmation,
      MtmCalculationResponse mtmResult,
      long quantity,
      Long expectedVersion,
      Long expectedOutstanding) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    Parties parties = parties(contract);
    requireCustodian(actor, parties);
    SettlementInstruction instruction = settlements.findLockedByInstructionReference(
            confirmation.instructionReference())
        .orElseThrow(() -> SetupException.missing("Return instruction not found"));
    validateInstructionContract(instruction, contract);
    if (instruction.getStatus() == SettlementInstructionStatus.SETTLED) {
      if (Objects.equals(instruction.getExternalConfirmationReference(),
          confirmation.confirmationReference())) return instruction;
      throw SetupException.conflict("Return instruction has a conflicting confirmation");
    }
    validateLoanOutstanding(contract);
    if (instruction.getStatus() != SettlementInstructionStatus.PENDING
        && instruction.getStatus() != SettlementInstructionStatus.PROCESSING
        && instruction.getStatus() != SettlementInstructionStatus.FAILED) {
      throw SetupException.conflict("Return instruction is not confirmable");
    }
    if (!Objects.equals(contract.getVersion(), expectedVersion)
        || !Objects.equals(contract.getOutstandingQuantity(), expectedOutstanding)
        || !Objects.equals(instruction.getQuantity(), BigDecimal.valueOf(quantity))
        || mtmResult.getInputVersion() == null
        || !mtmResult.getInputVersion().equals(contract.getVersion())
        || !Objects.equals(mtmResult.getOutstandingQuantity(), expectedOutstanding - quantity)) {
      throw SetupException.conflict("Contract or return inputs changed during valuation");
    }
    validateInstructionParties(instruction, parties, requestFor(contract));
    if (quantity > contract.getOutstandingQuantity()) {
      throw SetupException.conflict("Return quantity exceeds current outstanding quantity");
    }
    if (quantity > availableReturnQuantity(contract, instruction.getInstructionId())) {
      throw SetupException.conflict("Other pending returns exceed the remaining outstanding quantity");
    }

    RecallRequest recall = instruction.getRecallId() == null
        ? null
        : recalls.findLockedByRecallReference(recallReference(instruction))
            .filter(row -> row.getContractId().equals(contract.getContractId()))
            .orElseThrow(() -> SetupException.conflict("Return recall is no longer available"));
    if (recall != null
        && quantity > availableRecallReturnQuantity(recall, instruction.getInstructionId())) {
      throw SetupException.conflict("Return quantity exceeds the recall's remaining quantity");
    }
    if (confirmationAlreadyUsed(confirmation.confirmationReference(), instruction)) {
      throw SetupException.conflict("Confirmation reference was used for another instruction");
    }

    LoanRequest loanRequest = requestFor(contract);
    LenderInventory inventory = inventories.findLocked(
            parties.lenderId(), loanRequest.getSecurityIsin())
        .orElseThrow(() -> SetupException.conflict("Lender inventory not found"));
    if (inventory.getOnLoanQuantity() < quantity) {
      throw SetupException.conflict("On-loan inventory is insufficient for this return");
    }

    long newReturned = Math.addExact(contract.getReturnedQuantity(), quantity);
    long newOutstanding = contract.getDeliveredQuantity() - newReturned;
    String exposureKey = "RETURN:" + instruction.getInstructionReference();
    instruction.setExternalConfirmationReference(confirmation.confirmationReference());
    instruction.setStatus(SettlementInstructionStatus.SETTLED);
    instruction.setSettledAt(clock.instant());
    settlements.saveAndFlush(instruction);
    marginCalls.applyReturnValuation(
        contract, mtmResult, exposureKey, instruction.getInstructionId(), newOutstanding);
    contract.setReturnedQuantity(newReturned);
    if (newOutstanding == 0) contract.setContractStatus(LoanContractStatus.RETURNED);
    contracts.saveAndFlush(contract);
    inventory.setOnLoanQuantity(inventory.getOnLoanQuantity() - quantity);
    inventories.save(inventory);
    if (movements.existsBySettlementInstructionId(instruction.getInstructionId())) {
      throw SetupException.conflict("Return inventory movement was already recorded");
    }
    movements.save(InventoryMovement.builder()
        .contractId(contract.getContractId())
        .settlementInstructionId(instruction.getInstructionId())
        .securityIsin(instruction.getSecurityIsin())
        .fromOrganizationId(parties.borrowerId())
        .toOrganizationId(parties.lenderId())
        .quantity(quantity)
        .movementType(recall == null ? "RETURN" : "RECALL_RETURN")
        .movementTimestamp(instruction.getSettledAt())
        .build());
    if (recall != null) {
      updateRecallAfterReturn(recall, quantity, contract, parties, instruction);
    } else {
      reconcileVoluntaryReturnWithRecalls(quantity, contract, parties);
    }
    publishReturnUpdate(instruction, contract, parties, recallReference(instruction),
        "SETTLED", instruction.getVersion() + 1, true);
    return instruction;
  }

  private void updateRecallAfterReturn(
      RecallRequest recall,
      long quantity,
      LoanContract contract,
      Parties parties,
      SettlementInstruction instruction) {
    long returned = Math.addExact(recall.getReturnedQuantity(), quantity);
    recall.setReturnedQuantity(returned);
    saveRecallProgress(recall, contract, parties);
  }

  private void reconcileVoluntaryReturnWithRecalls(
      long quantity, LoanContract contract, Parties parties) {
    long remaining = quantity;
    // Apply voluntary returned quantity to the oldest active recall first.
    List<RecallRequest> activeRecalls = recalls
        .findByContractIdOrderByRequestedAtDesc(contract.getContractId())
        .stream()
        .filter(recall -> ACTIVE_RECALL_STATUSES.contains(recall.getRecallStatus()))
        .sorted(Comparator.comparing(RecallRequest::getRequestedAt))
        .toList();
    for (RecallRequest recall : activeRecalls) {
      if (remaining == 0) break;
      long unreturned = recall.getRequestedQuantity() - recall.getReturnedQuantity();
      long applied = Math.min(remaining, unreturned);
      if (applied > 0) {
        recall.setReturnedQuantity(Math.addExact(recall.getReturnedQuantity(), applied));
        saveRecallProgress(recall, contract, parties);
        remaining -= applied;
      }
    }
  }

  private void saveRecallProgress(
      RecallRequest recall, LoanContract contract, Parties parties) {
    long returned = recall.getReturnedQuantity();
    if (returned == recall.getRequestedQuantity()) {
      recall.setRecallStatus(RecallStatus.COMPLETED);
    } else if (recall.getRecallStatus() != RecallStatus.OVERDUE
        && recall.getRecallStatus() != RecallStatus.DEFAULTED) {
      recall.setRecallStatus(RecallStatus.PARTIALLY_RETURNED);
    }
    recalls.saveAndFlush(recall);
    publishRecall(recall, contract, parties, "RECALL_UPDATED");
  }

  private void publishRecall(
      RecallRequest recall, LoanContract contract, Parties parties, String eventType) {
    long version = recall.getVersion() == null ? 0L : recall.getVersion();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("aggregateVersion", version);
    payload.put("transactionReference", contract.getTransactionReference());
    payload.put("recallReference", recall.getRecallReference());
    payload.put("borrowerOrganizationReference", parties.borrowerReference());
    payload.put("custodianOrganizationReference", parties.custodianReference());
    payload.put("securityIsin", requestFor(contract).getSecurityIsin());
    payload.put("requestedQuantity", recall.getRequestedQuantity());
    payload.put("returnedQuantity", recall.getReturnedQuantity());
    payload.put("dueAt", recall.getDueAt().toString());
    payload.put("reason", recall.getRecallReason());
    payload.put("status", recall.getRecallStatus().name());
    outgoing.enqueue("RECALL", EventEnvelope.builder()
        .eventId(UUID.nameUUIDFromBytes(
            (eventType + ":" + recall.getRecallReference() + ":" + version)
                .getBytes(StandardCharsets.UTF_8)).toString())
        .eventType(eventType)
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(recall.getRecallReference())
        .aggregateReference(recall.getRecallReference())
        .aggregateVersion(version)
        .occurredAt(clock.instant())
        .senderOrganizationReference(parties.custodianReference())
        .receiverOrganizationReference(parties.borrowerReference())
        .payload(payload)
        .build());
  }

  private void publishReturnUpdate(
      SettlementInstruction instruction,
      LoanContract contract,
      Parties parties,
      String recallReference,
      String status,
      long aggregateVersion,
      boolean simulatedConfirmation) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("aggregateVersion", aggregateVersion);
    payload.put("transactionReference", contract.getTransactionReference());
    payload.put("instructionReference", instruction.getInstructionReference());
    payload.put("instructionType", "SECURITY_RETURN");
    payload.put("borrowerOrganizationReference", parties.borrowerReference());
    payload.put("custodianOrganizationReference", parties.custodianReference());
    payload.put("settlementStatus", status);
    payload.put("instructionStatus", status);
    payload.put("securityIsin", instruction.getSecurityIsin());
    payload.put("quantity", wholeQuantity(instruction.getQuantity()));
    payload.put("currency", instruction.getCurrency());
    payload.put("recallReference", recallReference);
    payload.put("contractStatus", contract.getContractStatus().name());
    payload.put("contractVersion", contract.getVersion());
    payload.put("deliveredQuantity", contract.getDeliveredQuantity());
    payload.put("returnedQuantity", contract.getReturnedQuantity());
    payload.put("outstandingQuantity", contract.getDeliveredQuantity() - contract.getReturnedQuantity());
    payload.put("settledAt", instruction.getSettledAt() == null
        ? null : instruction.getSettledAt().toString());
    payload.put("externalConfirmationReference", instruction.getExternalConfirmationReference());
    payload.put("failureReason", instruction.getFailureReason());
    payload.put("simulatedConfirmation", simulatedConfirmation);
    String eventType = "PENDING".equals(status)
        ? "SECURITY_RETURN_RECEIVED"
        : "SETTLEMENT_INSTRUCTION_UPDATED";
    outgoing.enqueue("SETTLEMENT_INSTRUCTION", EventEnvelope.builder()
        .eventId(UUID.nameUUIDFromBytes(
            (eventType + ":" + instruction.getInstructionReference() + ":" + aggregateVersion)
                .getBytes(StandardCharsets.UTF_8)).toString())
        .eventType(eventType)
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(instruction.getInstructionReference())
        .aggregateReference(instruction.getInstructionReference())
        .aggregateVersion(aggregateVersion)
        .occurredAt(clock.instant())
        .senderOrganizationReference(parties.custodianReference())
        .receiverOrganizationReference(parties.borrowerReference())
        .payload(payload)
        .build());
  }

  private boolean sameReturn(
      SettlementInstruction instruction,
      LoanContract contract,
      Parties parties,
      long quantity,
      String recallReference) {
    RecallRequest recall = instruction.getRecallId() == null
        ? null : recalls.findById(instruction.getRecallId()).orElse(null);
    return instruction.getContractId().equals(contract.getContractId())
        && "SECURITY_RETURN".equals(instruction.getInstructionType())
        && instruction.getFromOrganizationId().equals(parties.borrowerId())
        && instruction.getToOrganizationId().equals(parties.lenderId())
        && instruction.getQuantity() != null
        && instruction.getQuantity().compareTo(BigDecimal.valueOf(quantity)) == 0
        && Objects.equals(instruction.getSecurityIsin(), requestFor(contract).getSecurityIsin())
        && Objects.equals(instruction.getCurrency(), contract.getSettlementCurrency())
        && Objects.equals(recall == null ? null : recall.getRecallReference(), recallReference);
  }

  private void validateInstructionContract(SettlementInstruction instruction, LoanContract contract) {
    if (!instruction.getContractId().equals(contract.getContractId())
        || !"SECURITY_RETURN".equals(instruction.getInstructionType())) {
      throw SetupException.conflict("Return instruction belongs to another contract or workflow");
    }
  }

  private void validateInstructionParties(
      SettlementInstruction instruction, Parties parties, LoanRequest request) {
    if (!Objects.equals(instruction.getFromOrganizationId(), parties.borrowerId())
        || !Objects.equals(instruction.getToOrganizationId(), parties.lenderId())
        || !Objects.equals(instruction.getSecurityIsin(), request.getSecurityIsin())
        || !Objects.equals(instruction.getCurrency(), request.getSettlementCurrency())
        || wholeQuantity(instruction.getQuantity()) <= 0) {
      throw SetupException.conflict("Return instruction parties or approved security terms do not match");
    }
  }

  private boolean confirmationAlreadyUsed(String reference, SettlementInstruction current) {
    return settlements.findByExternalConfirmationReference(reference)
        .filter(other -> !other.getInstructionId().equals(current.getInstructionId()))
        .isPresent();
  }

  private long availableToRecall(LoanContract contract) {
    List<RecallRequest> contractRecalls =
        recalls.findByContractIdOrderByRequestedAtDesc(contract.getContractId());
    long activeRecallQuantity = contractRecalls.stream()
        .filter(recall -> ACTIVE_RECALL_STATUSES.contains(recall.getRecallStatus()))
        .mapToLong(recall -> recall.getRequestedQuantity() - recall.getReturnedQuantity())
        .sum();
    long voluntaryPending = pendingReturns(contract).stream()
        .filter(instruction -> instruction.getRecallId() == null)
        .mapToLong(instruction -> wholeQuantity(instruction.getQuantity()))
        .sum();
    return Math.max(0, contract.getOutstandingQuantity() - activeRecallQuantity - voluntaryPending);
  }

  private long availableReturnQuantity(LoanContract contract, Long excludingInstructionId) {
    long pending = pendingReturns(contract).stream()
        .filter(instruction -> !Objects.equals(
            instruction.getInstructionId(), excludingInstructionId))
        .mapToLong(instruction -> wholeQuantity(instruction.getQuantity()))
        .sum();
    return Math.max(0, contract.getOutstandingQuantity() - pending);
  }

  private long availableRecallReturnQuantity(RecallRequest recall, Long excludingInstructionId) {
    long pending = settlements.findByContractId(recall.getContractId()).stream()
        .filter(instruction -> Objects.equals(instruction.getRecallId(), recall.getRecallId()))
        .filter(instruction -> "SECURITY_RETURN".equals(instruction.getInstructionType()))
        .filter(instruction -> PENDING_STATUSES.contains(instruction.getStatus()))
        .filter(instruction -> !Objects.equals(
            instruction.getInstructionId(), excludingInstructionId))
        .mapToLong(instruction -> wholeQuantity(instruction.getQuantity()))
        .sum();
    return Math.max(0, recall.getRequestedQuantity() - recall.getReturnedQuantity() - pending);
  }

  private List<SettlementInstruction> pendingReturns(LoanContract contract) {
    return settlements.findByContractId(contract.getContractId()).stream()
        .filter(instruction -> "SECURITY_RETURN".equals(instruction.getInstructionType()))
        .filter(instruction -> PENDING_STATUSES.contains(instruction.getStatus()))
        .toList();
  }

  private RecallRequest lockedRecall(String reference, LoanContract contract) {
    RecallRequest recall = recalls.findLockedByRecallReference(reference)
        .filter(row -> row.getContractId().equals(contract.getContractId()))
        .orElseThrow(() -> SetupException.missing("Recall does not belong to this contract"));
    if (!ACTIVE_RECALL_STATUSES.contains(recall.getRecallStatus())) {
      throw SetupException.conflict("Recall is not accepting returns");
    }
    return recall;
  }

  private String recallReference(SettlementInstruction instruction) {
    return instruction.getRecallId() == null
        ? null
        : recalls.findById(instruction.getRecallId())
            .map(RecallRequest::getRecallReference)
            .orElse(null);
  }

  private Map<String, Object> recallView(RecallRequest recall, LoanContract contract) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("recallReference", recall.getRecallReference());
    view.put("transactionReference", contract.getTransactionReference());
    view.put("securityIsin", requestFor(contract).getSecurityIsin());
    view.put("requestedQuantity", recall.getRequestedQuantity());
    view.put("returnedQuantity", recall.getReturnedQuantity());
    view.put("outstandingQuantity", recall.getRequestedQuantity() - recall.getReturnedQuantity());
    view.put("dueAt", recall.getDueAt());
    view.put("reason", recall.getRecallReason());
    view.put("status", recall.getRecallStatus().name());
    view.put("version", recall.getVersion());
    return view;
  }

  private LoanContract contract(String reference) {
    return contracts.findByTransactionReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private LoanContract lockedContract(String reference) {
    return contracts.findLockedByTransactionReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private LoanRequest requestFor(LoanContract contract) {
    return requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
  }

  private Parties parties(LoanContract contract) {
    LoanRequest request = requestFor(contract);
    MslaAgreement agreement = agreements.findById(request.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Mandate not found"));
    Organization borrower = organizations.findById(agreement.getBorrowerOrgId())
        .orElseThrow(() -> SetupException.missing("Borrower organization not found"));
    Organization lender = organizations.findById(mandate.getLenderOrgId())
        .orElseThrow(() -> SetupException.missing("Lender organization not found"));
    Organization custodian = organizations.findById(mandate.getCustodianOrgId())
        .orElseThrow(() -> SetupException.missing("Custodian organization not found"));
    if (agreement.getAgreementStatus() != AgreementStatus.ACTIVE
        || mandate.getStatus() != MandateStatus.ACTIVE
        || borrower.getStatus() != OrganizationStatus.ACTIVE
        || lender.getStatus() != OrganizationStatus.ACTIVE
        || !contract.getSettlementCurrency().equals(request.getSettlementCurrency())) {
      throw SetupException.conflict("Contract parties or agreement are no longer active");
    }
    return new Parties(
        borrower.getOrganizationId(), borrower.getOrganizationReference(),
        lender.getOrganizationId(), custodian.getOrganizationId(),
        custodian.getOrganizationReference());
  }

  private void requireCustodian(Organization actor, Parties parties) {
    if (!actor.getOrganizationId().equals(parties.custodianId())) {
      throw new AccessDeniedException("Contract is outside the authenticated Custodian mandate");
    }
  }

  private void validateLoanOutstanding(LoanContract contract) {
    if (contract.getContractStatus() != LoanContractStatus.ON_LOAN
        || contract.getOutstandingQuantity() == null
        || contract.getOutstandingQuantity() <= 0) {
      throw SetupException.conflict("Only settled contracts with outstanding securities can be recalled or returned");
    }
  }

  private boolean validReference(String value) {
    return value != null && value.matches("[A-Za-z0-9._:-]{1,80}");
  }

  private long wholeQuantity(Object value) {
    try {
      return new BigDecimal(String.valueOf(value)).longValueExact();
    } catch (RuntimeException exception) {
      return -1;
    }
  }

  private long number(Object value) {
    try {
      return new BigDecimal(String.valueOf(value)).longValueExact();
    } catch (RuntimeException exception) {
      return -1;
    }
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private record Parties(
      Long borrowerId,
      String borrowerReference,
      Long lenderId,
      Long custodianId,
      String custodianReference) {}
}
