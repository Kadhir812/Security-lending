package com.hexaware.custodian_service.service.settlement;

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
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InitialSettlementService {
  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final CollateralRepository collaterals;
  private final SettlementInstructionRepository settlements;
  private final InventoryReservationRepository reservations;
  private final LenderInventoryRepository inventories;
  private final CounterpartyCreditLimitRepository creditLimits;
  private final LenderMandateRepository mandates;
  private final MslaAgreementRepository agreements;
  private final ContractStatusHistoryRepository history;
  private final MarginCallRepository marginCalls;
  private final MarginCallService marginCallService;
  private final OrganizationRepository organizations;
  private final InventoryMovementRepository inventoryMovements;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Map<String, Object> progress(Authentication authentication, String transactionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = scopedContract(transactionReference);
    LoanRequest loanRequest = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement = agreements.findById(loanRequest.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    if (!actor.getOrganizationId().equals(mandate.getCustodianOrgId())) {
      throw new AccessDeniedException("Contract is outside the authenticated Custodian mandate");
    }

    List<Map<String, Object>> collateralRows = collaterals
        .findByContractId(contract.getContractId()).stream()
        .map(collateral -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("instructionReference", collateral.getCollateralReference());
          row.put("collateralType", collateral.getCollateralType().name());
          row.put("amount", collateral.getPostedCashAmount());
          row.put("currency", collateral.getCurrency());
          row.put("status", collateral.getStatus());
          row.put("postedAt", collateral.getPostedAt());
          return row;
        })
        .toList();
    List<Map<String, Object>> settlementRows = settlements
        .findByContractId(contract.getContractId()).stream()
        .map(instruction -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("instructionReference", instruction.getInstructionReference());
          row.put("instructionType", instruction.getInstructionType());
          row.put("status", instruction.getStatus().name());
          row.put("currency", instruction.getCurrency());
          row.put("amount", instruction.getAmount());
          row.put("securityIsin", instruction.getSecurityIsin());
          row.put("quantity", instruction.getQuantity());
          row.put("settlementDate", instruction.getSettlementDate());
          row.put("settledAt", instruction.getSettledAt());
          row.put("externalConfirmationReference",
              instruction.getExternalConfirmationReference());
          return row;
        })
        .toList();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("transactionReference", contract.getTransactionReference());
    response.put("contractStatus", contract.getContractStatus().name());
    response.put("quantity", contract.getQuantity());
    response.put("deliveredQuantity", contract.getDeliveredQuantity());
    response.put("outstandingQuantity", contract.getOutstandingQuantity());
    response.put("requiredCashCollateral", requiredCollateral(contract));
    response.put("confirmedCashCollateral", confirmedCash(contract.getContractId()));
    response.put("collateralInstructions", collateralRows);
    response.put("settlementInstructions", settlementRows);
    return response;
  }

  @Transactional
  public void receiveCashInstruction(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String instructionReference = text(payload.get("instructionReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    if (!"COLLATERAL_INSTRUCTION_SUBMITTED".equals(event.getEventType())
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
        || transactionReference == null
        || instructionReference == null
        || borrowerReference == null
        || custodianReference == null
        || !"CASH".equals(payload.get("collateralType"))) {
      throw new com.hexaware.custodian_service.exception.InvalidEnvelopeException(
          "COLLATERAL_INSTRUCTION_ENVELOPE_MISMATCH");
    }

    LoanContract contract = scopedLockedContract(transactionReference);
    LoanRequest loanRequest = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement = agreements.findById(loanRequest.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    Organization borrower = organizations.findById(agreement.getBorrowerOrgId())
        .orElseThrow(() -> SetupException.missing("Borrower organization not found"));
    Organization custodian = organizations.findById(mandate.getCustodianOrgId())
        .orElseThrow(() -> SetupException.missing("Custodian organization not found"));
    if (!borrowerReference.equals(borrower.getOrganizationReference())
        || !custodianReference.equals(custodian.getOrganizationReference())) {
      throw new com.hexaware.custodian_service.exception.InvalidEnvelopeException(
          "COLLATERAL_INSTRUCTION_PARTIES_MISMATCH");
    }

    BigDecimal amount = decimal(payload.get("amount"));
    String currency = text(payload.get("currency"));
    if (amount.signum() <= 0 || amount.scale() > 2
        || !contract.getSettlementCurrency().equals(currency)) {
      throw SetupException.invalid("Initial CASH instruction amount or currency is invalid");
    }
    validateCash(contract,
        new com.hexaware.custodian_service.dto.request.CashCollateralInstructionRequest(
            instructionReference, currency, amount,
            "BORROWER-CASH", "CUSTODIAN-CASH", "CASH"));
    String marginCallReference = text(payload.get("marginCallReference"));
    MarginCall call = null;
    if (marginCallReference == null) {
      validateApprovedContract(contract);
    } else {
      if (contract.getContractStatus() != LoanContractStatus.ON_LOAN) {
        throw SetupException.conflict("Top-up instructions require an on-loan contract");
      }
      call = marginCalls.findByMarginCallReference(marginCallReference)
          .orElseThrow(() -> SetupException.missing("Margin call not found"));
      call = marginCalls.findLockedById(call.getMarginCallId())
          .orElseThrow(() -> SetupException.missing("Margin call not found"));
      if (!call.getContractId().equals(contract.getContractId())
          || !List.of(MarginCallStatus.OPEN, MarginCallStatus.PARTIALLY_SATISFIED,
              MarginCallStatus.BREACHED).contains(call.getStatus())) {
        throw SetupException.conflict("Margin call is not eligible for a CASH top-up");
      }
    }

    Optional<SettlementInstruction> existing =
        settlements.findByInstructionReference(instructionReference);
    if (existing.isPresent()) {
      SettlementInstruction instruction = existing.get();
      Collateral collateral = instruction.getCollateralId() == null
          ? null
          : collaterals.findById(instruction.getCollateralId()).orElse(null);
      if (!Objects.equals(instruction.getContractId(), contract.getContractId())
          || !"COLLATERAL_TRANSFER".equals(instruction.getInstructionType())
          || !Objects.equals(instruction.getAmount(), amount)
          || !Objects.equals(instruction.getCurrency(), currency)
          || collateral == null
          || !Objects.equals(collateral.getPostedCashAmount(), amount)
          || !Objects.equals(instruction.getMarginCallId(),
              call == null ? null : call.getMarginCallId())) {
        throw SetupException.conflict("Collateral instruction reference was reused");
      }
      return;
    }
    Optional<Collateral> priorCollateral =
        collaterals.findByCollateralReference(instructionReference);
    if (priorCollateral.isPresent()) {
      throw SetupException.conflict("Collateral instruction already exists without settlement");
    }

    Instant receivedAt = clock.instant();
    Collateral collateral = collaterals.save(Collateral.builder()
        .collateralReference(instructionReference)
        .contractId(contract.getContractId())
        .collateralType(CollateralType.CASH)
        .currency(currency)
        .postedCashAmount(amount)
        .appliedHaircutPct(BigDecimal.ZERO)
        .status("PENDING")
        .build());
    SettlementInstruction instruction = SettlementInstruction.builder()
        .instructionReference(instructionReference)
        .contractId(contract.getContractId())
        .collateralId(collateral.getCollateralId())
        .marginCallId(call == null ? null : call.getMarginCallId())
        .instructionType("COLLATERAL_TRANSFER")
        .fromOrganizationId(borrower.getOrganizationId())
        .toOrganizationId(custodian.getOrganizationId())
        .fromAccount("BORROWER-CASH")
        .toAccount("CUSTODIAN-CASH")
        .amount(amount)
        .currency(currency)
        .status(SettlementInstructionStatus.PENDING)
        .settlementDate(call == null
            ? contract.getSettlementDate()
            : receivedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate())
        .createdAt(receivedAt)
        .build();
    settlements.save(instruction);
    Map<String, Object> receipt = settlementPayload(
        contract, instruction, borrower, custodian, "PENDING", false);
    receipt.put("payloadVersion", 1);
    receipt.put("aggregateVersion", 1L);
    receipt.put("instructionStatus", "PENDING");
    receipt.put("marginCallReference", marginCallReference);
    outgoing.enqueue("SETTLEMENT_INSTRUCTION", EventEnvelope.builder()
        .eventId(UUID.nameUUIDFromBytes(
            ("COLLATERAL_INSTRUCTION_RECEIVED:" + instructionReference)
                .getBytes(StandardCharsets.UTF_8)).toString())
        .eventType("COLLATERAL_INSTRUCTION_RECEIVED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(instructionReference)
        .causationEventId(event.getEventId())
        .aggregateReference(instructionReference)
        .aggregateVersion(1L)
        .occurredAt(receivedAt)
        .senderOrganizationReference(custodianReference)
        .receiverOrganizationReference(borrowerReference)
        .payload(receipt)
        .build());
  }

  @Transactional
  public Collateral submitCash(Authentication authentication, String transactionReference,
      com.hexaware.custodian_service.dto.request.CashCollateralInstructionRequest request) {
    access.actor(authentication, true);
    LoanContract contract = scopedContract(transactionReference);
    validateApprovedContract(contract);
    validateCash(contract, request);

    Optional<Collateral> existing = collaterals.findByCollateralReference(request.instructionReference());
    if (existing.isPresent()) {
      if (!existing.get().getContractId().equals(contract.getContractId())
          || existing.get().getPostedCashAmount().compareTo(request.amount()) != 0) {
        throw SetupException.conflict("Collateral instruction reference was reused");
      }
      return existing.get();
    }
    return collaterals.save(Collateral.builder()
        .collateralReference(request.instructionReference())
        .contractId(contract.getContractId())
        .collateralType(CollateralType.CASH)
        .currency(contract.getSettlementCurrency())
        .postedCashAmount(request.amount())
        .appliedHaircutPct(BigDecimal.ZERO)
        .status("PENDING")
        .build());
  }

  @Transactional
  public SettlementInstruction confirmCash(Authentication authentication, String transactionReference,
      com.hexaware.custodian_service.dto.request.SimulatedSettlementConfirmationRequest request) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = scopedLockedContract(transactionReference);
    LoanRequest loanRequest = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement = agreements.findById(loanRequest.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    if (!actor.getOrganizationId().equals(mandate.getCustodianOrgId())) {
      throw new AccessDeniedException("Contract is outside the authenticated Custodian mandate");
    }
    Collateral collateral = collaterals.findByCollateralReference(request.instructionReference())
        .orElseThrow(() -> SetupException.missing("Cash collateral instruction not found"));
    if (!collateral.getContractId().equals(contract.getContractId())) {
      throw SetupException.conflict("Collateral belongs to another contract");
    }
    Optional<SettlementInstruction> byConfirmation =
        settlements.findByExternalConfirmationReference(request.confirmationReference());
    if (byConfirmation.isPresent()) {
      SettlementInstruction existing = byConfirmation.get();
      if (!existing.getInstructionReference().equals(request.instructionReference())) {
        throw SetupException.conflict("Confirmation reference was reused for another instruction");
      }
      return existing;
    }
    SettlementInstruction instruction = settlements.findByInstructionReference(request.instructionReference())
        .orElse(null);
    if (instruction == null || instruction.getMarginCallId() == null) {
      validateApprovedContract(contract);
    } else if (contract.getContractStatus() != LoanContractStatus.ON_LOAN) {
      throw SetupException.conflict("Margin-call top-up is available only for an on-loan contract");
    }
    if (instruction != null && instruction.getStatus() == SettlementInstructionStatus.SETTLED) {
      if (request.confirmationReference().equals(
          instruction.getExternalConfirmationReference())) {
        return instruction;
      }
      throw SetupException.conflict("A conflicting confirmation already settled this instruction");
    }
    if (instruction == null) {
      instruction = settlements.save(SettlementInstruction.builder()
          .instructionReference(request.instructionReference())
          .contractId(contract.getContractId())
          .collateralId(collateral.getCollateralId())
          .instructionType("COLLATERAL_TRANSFER")
          .fromOrganizationId(borrowerOrganizationId(loanRequest))
          .toOrganizationId(actor.getOrganizationId())
          .fromAccount("BORROWER-CASH")
          .toAccount("CUSTODIAN-CASH")
          .amount(collateral.getPostedCashAmount())
          .currency(contract.getSettlementCurrency())
          .status(SettlementInstructionStatus.PROCESSING)
          .settlementDate(clock.instant().atZone(java.time.ZoneOffset.UTC).toLocalDate())
          .createdAt(clock.instant())
          .build());
    }
    if (instruction.getContractId() == null
        || !instruction.getContractId().equals(contract.getContractId())
        || (instruction.getCollateralId() != null
            && !instruction.getCollateralId().equals(collateral.getCollateralId()))
        || instruction.getAmount() == null
        || instruction.getAmount().compareTo(collateral.getPostedCashAmount()) != 0
        || !instruction.getFromOrganizationId().equals(borrowerOrganizationId(loanRequest))
        || !instruction.getToOrganizationId().equals(actor.getOrganizationId())
        || !contract.getSettlementCurrency().equals(instruction.getCurrency())) {
      throw SetupException.conflict("Settlement instruction does not match collateral and contract");
    }
    instruction.setExternalConfirmationReference(request.confirmationReference());
    instruction.setStatus(SettlementInstructionStatus.SETTLED);
    instruction.setSettledAt(clock.instant());
    collateral.setStatus("POSTED");
    collateral.setPostedAt(clock.instant());
    collateral.setAppliedHaircutPct(BigDecimal.ZERO);
    settlements.save(instruction);
    collaterals.saveAndFlush(collateral);
    if (instruction.getMarginCallId() != null) {
      MarginCall call = marginCalls.findLockedById(instruction.getMarginCallId())
          .orElseThrow(() -> SetupException.missing("Margin call not found"));
      if (!call.getContractId().equals(contract.getContractId())) {
        throw SetupException.conflict("Top-up settlement is linked to another contract");
      }
      marginCallService.recordTopUp(
          contract, call, instruction.getInstructionId());
    } else {
      LoanContractStatus oldStatus = contract.getContractStatus();
      contract.setContractStatus(LoanContractStatus.PENDING_SETTLEMENT);
      contracts.saveAndFlush(contract);
      if (oldStatus != LoanContractStatus.PENDING_SETTLEMENT) {
        history.save(ContractStatusHistory.builder()
            .contractId(contract.getContractId())
            .oldStatus(oldStatus.name())
            .newStatus(LoanContractStatus.PENDING_SETTLEMENT.name())
            .changedBy(access.user(authentication).getUserId())
            .actorType(HistoryActorType.USER)
            .reason("SIMULATED_INITIAL_CASH_COLLATERAL_CONFIRMED")
            .changedAt(clock.instant())
            .build());
      }
      publishSettlementUpdate(contract, instruction, loanRequest, 2L);
    }
    return instruction;
  }

  @Transactional
  public SettlementInstruction confirmSecurities(Authentication authentication, String transactionReference,
      com.hexaware.custodian_service.dto.request.SimulatedSettlementConfirmationRequest request) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = scopedLockedContract(transactionReference);
    LoanRequest loanRequest = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement = agreements.findById(loanRequest.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    if (!actor.getOrganizationId().equals(mandate.getCustodianOrgId())) {
      throw new AccessDeniedException("Contract is outside the authenticated Custodian mandate");
    }
    if (contract.getContractStatus() == LoanContractStatus.ON_LOAN) {
      SettlementInstruction settled = settlements.findByInstructionReference(
              request.instructionReference())
          .orElseThrow(() -> SetupException.conflict(
              "Contract is already on loan from a different delivery instruction"));
      if (settled.getContractId().equals(contract.getContractId())
          && "SECURITY_DELIVERY".equals(settled.getInstructionType())
          && settled.getStatus() == SettlementInstructionStatus.SETTLED
          && Objects.equals(settled.getExternalConfirmationReference(),
              request.confirmationReference())) {
        return settled;
      }
      throw SetupException.conflict("Contract delivery is already confirmed");
    }
    validateApprovedContract(contract);
    BigDecimal required = requiredCollateral(contract);
    BigDecimal confirmed = confirmedCash(contract.getContractId());
    if (confirmed.compareTo(required) < 0) {
      throw SetupException.conflict("Full initial CASH collateral is required before securities delivery");
    }

    Optional<SettlementInstruction> existing =
        settlements.findByInstructionReference(request.instructionReference());
    SettlementInstruction instruction;
    if (existing.isPresent()) {
      instruction = existing.get();
      if (!Objects.equals(instruction.getContractId(), contract.getContractId())
          || !"SECURITY_DELIVERY".equals(instruction.getInstructionType())) {
        throw SetupException.conflict("Instruction reference belongs to another settlement");
      }
      if (instruction.getStatus() == SettlementInstructionStatus.SETTLED) {
        if (Objects.equals(instruction.getExternalConfirmationReference(),
            request.confirmationReference())) {
          return instruction;
        }
        throw SetupException.conflict("A conflicting confirmation already settled this instruction");
      }
      if (instruction.getStatus() != SettlementInstructionStatus.PENDING
          && instruction.getStatus() != SettlementInstructionStatus.PROCESSING) {
        throw SetupException.conflict("Securities instruction is not confirmable");
      }
    } else {
      instruction = SettlementInstruction.builder()
          .instructionReference(request.instructionReference())
          .contractId(contract.getContractId())
          .instructionType("SECURITY_DELIVERY")
          .fromOrganizationId(lenderOrganizationId(loanRequest))
          .toOrganizationId(borrowerOrganizationId(loanRequest))
          .fromAccount("LENDER-SECURITIES")
          .toAccount("BORROWER-SECURITIES")
          .securityIsin(loanRequest.getSecurityIsin())
          .quantity(BigDecimal.valueOf(contract.getQuantity()))
          .currency(contract.getSettlementCurrency())
          .status(SettlementInstructionStatus.PENDING)
          .settlementDate(contract.getSettlementDate())
          .createdAt(clock.instant())
          .build();
      instruction = settlements.save(instruction);
    }

    Optional<SettlementInstruction> confirmation =
        settlements.findByExternalConfirmationReference(request.confirmationReference());
    if (confirmation.isPresent()) {
      if (!confirmation.get().getInstructionId().equals(instruction.getInstructionId())) {
        throw SetupException.conflict("Confirmation reference was reused for another instruction");
      }
      if (instruction.getStatus() == SettlementInstructionStatus.SETTLED) return instruction;
      throw SetupException.conflict("Confirmation reference is already attached to an unsettled instruction");
    }
    if (instruction.getSecurityIsin() == null
        || !loanRequest.getSecurityIsin().equals(instruction.getSecurityIsin())
        || instruction.getQuantity() == null
        || instruction.getQuantity().compareTo(BigDecimal.valueOf(contract.getQuantity())) != 0
        || !Objects.equals(instruction.getFromOrganizationId(), lenderOrganizationId(loanRequest))
        || !Objects.equals(instruction.getToOrganizationId(), borrowerOrganizationId(loanRequest))
        || !Objects.equals(instruction.getCurrency(), contract.getSettlementCurrency())) {
      throw SetupException.conflict("Securities instruction does not match approved contract terms");
    }
    instruction.setExternalConfirmationReference(request.confirmationReference());
    instruction.setStatus(SettlementInstructionStatus.SETTLED);
    instruction.setSettledAt(clock.instant());

    InventoryReservation reservation = reservations.findLockedByContractId(contract.getContractId())
        .orElseThrow(() -> SetupException.conflict("Contract has no inventory reservation"));
    if (reservation.getStatus() != InventoryReservationStatus.ACTIVE
        || reservation.getRemainingQuantity() == null) {
      throw SetupException.conflict("Inventory reservation is not active");
    }
    if (!reservation.getRemainingQuantity().equals(contract.getQuantity())) {
      throw SetupException.conflict("Delivery must consume the full approved reservation");
    }
    LenderInventory inventory = inventories.findLocked(
        reservationOrganizationId(reservation), loanRequest.getSecurityIsin())
        .orElseThrow(() -> SetupException.conflict("Reserved lender inventory not found"));
    reservation.setConsumedQuantity(contract.getQuantity());
    reservation.setStatus(InventoryReservationStatus.CONSUMED);
    inventory.setReservedQuantity(inventory.getReservedQuantity() - contract.getQuantity());
    inventory.setOnLoanQuantity(inventory.getOnLoanQuantity() + contract.getQuantity());

    CounterpartyCreditLimit credit = creditLimits.findLockedByMslaId(agreement.getMslaId())
        .orElseThrow(() -> SetupException.conflict("Counterparty credit limit not found"));
    if (credit.getReservedAmount().compareTo(contract.getReservedCreditAmount()) < 0) {
      throw SetupException.conflict("Reserved credit is insufficient");
    }
    credit.setReservedAmount(credit.getReservedAmount().subtract(contract.getReservedCreditAmount()));
    credit.setUsedAmount(credit.getUsedAmount().add(contract.getReservedCreditAmount()));
    contract.setUsedCreditAmount(contract.getReservedCreditAmount());
    LoanContractStatus oldContractStatus = contract.getContractStatus();
    contract.setDeliveredQuantity(contract.getQuantity());
    contract.setContractStatus(LoanContractStatus.ON_LOAN);

    settlements.saveAndFlush(instruction);
    if (inventoryMovements.existsBySettlementInstructionId(instruction.getInstructionId())) {
      throw SetupException.conflict("Securities movement was already recorded");
    }
    inventoryMovements.save(InventoryMovement.builder()
        .contractId(contract.getContractId())
        .settlementInstructionId(instruction.getInstructionId())
        .securityIsin(instruction.getSecurityIsin())
        .fromOrganizationId(instruction.getFromOrganizationId())
        .toOrganizationId(instruction.getToOrganizationId())
        .quantity(contract.getQuantity())
        .movementType("LOAN_DELIVERY")
        .movementTimestamp(instruction.getSettledAt())
        .build());
    reservations.save(reservation);
    inventories.save(inventory);
    creditLimits.save(credit);
    contracts.saveAndFlush(contract);
    history.save(ContractStatusHistory.builder()
        .contractId(contract.getContractId()).oldStatus(oldContractStatus.name())
        .newStatus(LoanContractStatus.ON_LOAN.name())
        .changedBy(access.user(authentication).getUserId())
        .actorType(HistoryActorType.USER)
        .reason("SIMULATED_INITIAL_SECURITIES_DELIVERY_CONFIRMED")
        .changedAt(clock.instant()).build());
    publishSettlementUpdate(contract, instruction, loanRequest, 1L);
    return instruction;
  }

  private void publishSettlementUpdate(
      LoanContract contract, SettlementInstruction instruction, LoanRequest loanRequest,
      long aggregateVersion) {
    MslaAgreement agreement = agreements.findById(loanRequest.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    Organization borrower = organizations.findById(agreement.getBorrowerOrgId())
        .orElseThrow(() -> SetupException.missing("Borrower organization not found"));
    Organization custodian = organizations.findById(mandate.getCustodianOrgId())
        .orElseThrow(() -> SetupException.missing("Custodian organization not found"));
    Map<String, Object> payload =
        settlementPayload(contract, instruction, borrower, custodian, "SETTLED", true);
    payload.put("payloadVersion", 1);
    payload.put("instructionStatus", "SETTLED");
    payload.put("aggregateVersion", aggregateVersion);
    String eventType = "SETTLEMENT_INSTRUCTION_UPDATED";
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
        .senderOrganizationReference(custodian.getOrganizationReference())
        .receiverOrganizationReference(borrower.getOrganizationReference())
        .payload(payload)
        .build());
  }

  private Map<String, Object> settlementPayload(
      LoanContract contract,
      SettlementInstruction instruction,
      Organization borrower,
      Organization custodian,
      String settlementStatus,
      boolean simulatedConfirmation) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("transactionReference", contract.getTransactionReference());
    payload.put("instructionReference", instruction.getInstructionReference());
    payload.put("instructionType", instruction.getInstructionType());
    payload.put("borrowerOrganizationReference", borrower.getOrganizationReference());
    payload.put("custodianOrganizationReference", custodian.getOrganizationReference());
    payload.put("settlementStatus", settlementStatus);
    payload.put("currency", instruction.getCurrency());
    if ("COLLATERAL_TRANSFER".equals(instruction.getInstructionType())) {
      payload.put("collateralType", "CASH");
    }
    payload.put("amount", instruction.getAmount() == null
        ? null
        : instruction.getAmount().toPlainString());
    payload.put("quantity", instruction.getQuantity() == null
        ? null
        : instruction.getQuantity().longValueExact());
    payload.put("securityIsin", instruction.getSecurityIsin());
    payload.put("contractStatus", contract.getContractStatus().name());
    payload.put("contractVersion", contract.getVersion());
    payload.put("deliveredQuantity", contract.getDeliveredQuantity());
    payload.put("outstandingQuantity",
        Math.subtractExact(nvl(contract.getDeliveredQuantity()), nvl(contract.getReturnedQuantity())));
    payload.put("confirmedCashCollateral", confirmedCash(contract.getContractId()).toPlainString());
    payload.put("settledAt", instruction.getSettledAt() == null
        ? null
        : instruction.getSettledAt().toString());
    payload.put("externalConfirmationReference", instruction.getExternalConfirmationReference());
    payload.put("simulatedConfirmation", simulatedConfirmation);
    return payload;
  }

  private void validateCash(LoanContract contract,
      com.hexaware.custodian_service.dto.request.CashCollateralInstructionRequest request) {
    if (!"CASH".equals(request.collateralType())
        || !contract.getSettlementCurrency().equals(request.currency())
        || request.amount() == null || request.amount().signum() <= 0) {
      throw SetupException.invalid("Initial CASH instruction is invalid");
    }
    if (!"CASH".equals(String.valueOf(contract.getCollateralTerms().get("collateralType")))
        || contract.getCollateralTerms().get("currency") == null
        || !contract.getSettlementCurrency().equals(contract.getCollateralTerms().get("currency"))) {
      throw SetupException.conflict("Contract collateral snapshot is not CASH in contract currency");
    }
  }

  private void validateApprovedContract(LoanContract contract) {
    if (contract.getContractStatus() != LoanContractStatus.APPROVED
        && contract.getContractStatus() != LoanContractStatus.PENDING_SETTLEMENT) {
      throw SetupException.conflict("Contract is not in an initial settlement state");
    }
  }

  private LoanContract scopedContract(String reference) {
    LoanContract contract = contracts.findByTransactionReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
    return contract;
  }

  private LoanContract scopedLockedContract(String reference) {
    return contracts.findLockedByTransactionReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private BigDecimal requiredCollateral(LoanContract contract) {
    Object value = contract.getCollateralTerms().get("requiredCashCollateral");
    if (value == null) value = contract.getCollateralTerms().get("requiredAmount");
    try {
      return value instanceof Number n ? new BigDecimal(n.toString()).setScale(2)
          : value instanceof String s ? new BigDecimal(s).setScale(2)
          : contract.getLatestRequiredCollateral();
    } catch (RuntimeException exception) {
      throw SetupException.conflict("Approved collateral requirement is missing or invalid");
    }
  }

  private BigDecimal confirmedCash(Long contractId) {
    return collaterals.findByContractId(contractId).stream()
        .filter(c -> "POSTED".equals(c.getStatus()) && c.getCollateralType() == CollateralType.CASH)
        .map(Collateral::getPostedCashAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Long borrowerOrganizationId(LoanRequest request) {
    MslaAgreement agreement = agreements.findById(request.getMslaId()).orElseThrow();
    return agreement.getBorrowerOrgId();
  }

  private Long lenderOrganizationId(LoanRequest request) {
    MslaAgreement agreement = agreements.findById(request.getMslaId()).orElseThrow();
    LenderMandate mandate = mandates.findById(agreement.getMandateId()).orElseThrow();
    return mandate.getLenderOrgId();
  }

  private Long reservationOrganizationId(InventoryReservation reservation) {
    return inventories.findById(reservation.getInventoryId()).map(LenderInventory::getOrganizationId)
        .orElseThrow(() -> SetupException.conflict("Reserved inventory is unavailable"));
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw new com.hexaware.custodian_service.exception.InvalidEnvelopeException(
          "INVALID_COLLATERAL_INSTRUCTION_AMOUNT");
    }
  }

  private long number(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw new com.hexaware.custodian_service.exception.InvalidEnvelopeException(
          "INVALID_COLLATERAL_INSTRUCTION_VERSION");
    }
  }

  private long nvl(Long value) {
    return value == null ? 0 : value;
  }
}
