package com.hexaware.custodian_service.service.collateral;

import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.ContractStatusHistory;
import com.hexaware.custodian_service.entity.LendingFeePayment;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.MarginCall;
import com.hexaware.custodian_service.entity.RecallRequest;
import com.hexaware.custodian_service.entity.RebatePaymentHistory;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.HistoryActorType;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.MarginCallStatus;
import com.hexaware.custodian_service.enums.RecallStatus;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.ContractStatusHistoryRepository;
import com.hexaware.custodian_service.repository.LendingFeePaymentRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.MarginCallRepository;
import com.hexaware.custodian_service.repository.RecallRequestRepository;
import com.hexaware.custodian_service.repository.RebatePaymentHistoryRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.fees.LendingFeeAccrualService;
import com.hexaware.custodian_service.service.fees.CashRebateAccrualService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractClosureService {
  private static final List<RecallStatus> ACTIVE_RECALLS =
      List.of(
          RecallStatus.OPEN,
          RecallStatus.PROCESSING,
          RecallStatus.PARTIALLY_RETURNED,
          RecallStatus.OVERDUE,
          RecallStatus.DEFAULTED);
  private static final List<MarginCallStatus> ACTIVE_MARGIN_CALLS =
      List.of(
          MarginCallStatus.OPEN,
          MarginCallStatus.PARTIALLY_SATISFIED,
          MarginCallStatus.BREACHED);

  private final LoanContractRepository contracts;
  private final SetupAccess access;
  private final ContractStatusHistoryRepository histories;
  private final SettlementInstructionRepository settlements;
  private final CollateralRepository collaterals;
  private final RecallRequestRepository recalls;
  private final MarginCallRepository marginCalls;
  private final LendingFeePaymentRepository feePayments;
  private final RebatePaymentHistoryRepository rebatePayments;
  private final LendingFeeAccrualService accruals;
  private final CashRebateAccrualService rebateAccruals;
  private final FinalCollateralReleaseEvents events;
  private final Clock clock;

  @Transactional
  public Map<String, Object> close(
      Authentication authentication,
      LoanContract contract,
      FinalReleaseParties parties,
      String closureReference,
      String reason) {
    String transactionReference = contract.getTransactionReference();
    String eventId =
        FinalCollateralReleaseRules.closureEventId(transactionReference, closureReference);
    if (contract.getContractStatus() == LoanContractStatus.CLOSED) {
      ContractStatusHistory existing =
          histories
              .findByContractIdAndSourceEventId(contract.getContractId(), eventId)
              .orElseThrow(
                  () ->
                      SetupException.conflict(
                          "Contract was closed with a different closure reference"));
      if (!Objects.equals(existing.getReason(), reason)) {
        throw SetupException.conflict("Closure reference was reused with a different reason");
      }
      return Map.of(
          "transactionReference",
          transactionReference,
          "status",
          "CLOSED",
          "closureReference",
          closureReference,
          "idempotent",
          true);
    }

    FinalCollateralReleaseRules.validateReturnedContract(contract);
    completeFinalAccrual(authentication, contract);
    assertFinalFeesSettled(authentication, contract);
    assertFinalRebatesSettled(authentication, contract);
    assertNoActiveCallsOrUnresolvedSettlements(contract, null);
    assertNoHeldCollateral(contract);
    if (FinalCollateralReleaseRules.nvl(contract.getUsedCreditAmount()).signum() != 0
        || FinalCollateralReleaseRules.nvl(contract.getReservedCreditAmount()).signum() != 0) {
      throw SetupException.conflict("Contract credit exposure or reservation remains outstanding");
    }

    LoanContractStatus previous = contract.getContractStatus();
    contract.setContractStatus(LoanContractStatus.CLOSED);
    contracts.saveAndFlush(contract);
    histories.save(
        ContractStatusHistory.builder()
            .contractId(contract.getContractId())
            .oldStatus(previous.name())
            .newStatus(LoanContractStatus.CLOSED.name())
            .changedBy(access.user(authentication).getUserId())
            .actorType(HistoryActorType.USER)
            .reason(reason)
            .sourceEventId(eventId)
            .changedAt(clock.instant())
            .build());
    events.publishClosed(contract, parties, closureReference, reason, eventId);
    return Map.of(
        "transactionReference",
        transactionReference,
        "status",
        "CLOSED",
        "closureReference",
        closureReference,
        "contractVersion",
        contract.getVersion(),
        "idempotent",
        false);
  }

  Map<String, Object> completeFinalAccrual(
      Authentication authentication, LoanContract contract) {
    List<SettlementInstruction> history =
        settlements.findByContractIdAndStatusOrderBySettledAtAsc(
            contract.getContractId(), SettlementInstructionStatus.SETTLED);
    LocalDate firstDelivery =
        history.stream()
            .filter(row -> "SECURITY_DELIVERY".equals(row.getInstructionType()))
            .map(SettlementInstruction::getSettledAt)
            .filter(Objects::nonNull)
            .map(value -> value.atZone(clock.getZone()).toLocalDate())
            .min(LocalDate::compareTo)
            .orElseThrow(
                () -> SetupException.conflict("Confirmed securities delivery history is missing"));
    LocalDate lastReturn =
        history.stream()
            .filter(row -> "SECURITY_RETURN".equals(row.getInstructionType()))
            .map(SettlementInstruction::getSettledAt)
            .filter(Objects::nonNull)
            .map(value -> value.atZone(clock.getZone()).toLocalDate())
            .max(LocalDate::compareTo)
            .orElseThrow(
                () -> SetupException.conflict("Confirmed securities return history is missing"));
    LocalDate today = LocalDate.now(clock);
    if (!lastReturn.isBefore(today)) {
      throw SetupException.conflict(
          "Final accrual is available after the IST return settlement date completes");
    }
    LocalDate through = today.minusDays(1);
    if (firstDelivery.isAfter(through)) {
      throw SetupException.conflict("No completed accrual dates are available yet");
    }
    Map<String, Object> result =
        accruals.accrue(
            authentication, contract.getTransactionReference(), firstDelivery, through, null);
    Object pending = result.get("pending");
    if (pending instanceof List<?> values && !values.isEmpty()) {
      throw SetupException.conflict("Final accrual has unresolved dates: " + values);
    }
    return result;
  }

  void assertFinalFeesSettled(Authentication authentication, LoanContract contract) {
    for (Map<String, Object> charge :
        accruals.charges(authentication, contract.getTransactionReference())) {
      if (FinalCollateralReleaseRules.decimal(charge.get("outstandingAmount")).signum() != 0
          || FinalCollateralReleaseRules.decimal(charge.get("pendingAmount")).signum() != 0
          || FinalCollateralReleaseRules.decimal(charge.get("creditAmount")).signum() != 0) {
        throw SetupException.conflict(
            "Final release is blocked by unpaid, pending, or credit lending-fee balances");
      }
    }
    List<LendingFeePayment> payments = feePayments.findLockedByContractId(contract.getContractId());
    if (payments.stream().anyMatch(payment -> "PENDING".equals(payment.getStatus()))) {
      throw SetupException.conflict("Fee payment instructions must be resolved before final release");
    }
  }

  void assertFinalRebatesSettled(Authentication authentication, LoanContract contract) {
    List<RebatePaymentHistory> payments =
        rebatePayments.findLockedByContractId(contract.getContractId());
    for (Map<String, Object> rebate :
        rebateAccruals.rebates(authentication, contract.getTransactionReference())) {
      BigDecimal amount = FinalCollateralReleaseRules.decimal(rebate.get("amount"));
      if (amount.signum() < 0) {
        throw SetupException.conflict("Cash-rebate accrual has an invalid negative balance");
      }
      if (amount.signum() == 0) continue;
      String itemKey = String.valueOf(rebate.get("itemKey"));
      String resultReference = String.valueOf(rebate.get("latestResultReference"));
      boolean paid =
          payments.stream()
              .anyMatch(
                  payment ->
                      itemKey.equals(payment.getObligationReference())
                          && resultReference.equals(payment.getCalculationResultReference())
                          && "PAID".equals(payment.getStatus())
                          && payment.getPaymentAmount().compareTo(amount) == 0);
      if (!paid) {
        throw SetupException.conflict(
            "Final release is blocked by an unpaid or unresolved cash-rebate accrual");
      }
    }
  }

  void assertNoActiveCallsOrUnresolvedSettlements(
      LoanContract contract, String releaseReference) {
    List<RecallRequest> recallRows =
        recalls.findByContractIdOrderByRequestedAtDesc(contract.getContractId());
    if (recallRows.stream().anyMatch(row -> ACTIVE_RECALLS.contains(row.getRecallStatus()))) {
      throw SetupException.conflict("An active recall must be reconciled before final release");
    }
    List<MarginCall> callRows =
        marginCalls.findByContractIdOrderByCreatedAtDesc(contract.getContractId());
    if (callRows.stream().anyMatch(row -> ACTIVE_MARGIN_CALLS.contains(row.getStatus()))) {
      throw SetupException.conflict("An active margin call must be reconciled before final release");
    }
    for (SettlementInstruction instruction : settlements.findByContractId(contract.getContractId())) {
      if (instruction.getStatus() != SettlementInstructionStatus.PENDING
          && instruction.getStatus() != SettlementInstructionStatus.PROCESSING) continue;
      if ("COLLATERAL_RELEASE".equals(instruction.getInstructionType())
          && releaseReference != null
          && releaseReference.equals(
              FinalCollateralReleaseRules.releaseReference(instruction.getInstructionReference()))) {
        continue;
      }
      throw SetupException.conflict("Unresolved settlement instructions remain on the contract");
    }
  }

  void assertNoHeldCollateral(LoanContract contract) {
    for (Collateral collateral : collaterals.findLockedByContractId(contract.getContractId())) {
      if ("CASH".equals(collateral.getCollateralType().name())) {
        if ("FAILED".equals(collateral.getStatus())
            && FinalCollateralReleaseRules.nvl(collateral.getReleasedCashAmount()).signum() == 0) {
          continue;
        }
        FinalCollateralReleaseRules.validateCashCollateral(contract, collateral);
        if (!collateral.getPostedCashAmount().equals(collateral.getReleasedCashAmount())) {
          throw SetupException.conflict("Confirmed CASH collateral remains held");
        }
      } else if (collateral.getPledgedQuantity() != null
          && collateral
                  .getPledgedQuantity()
                  .compareTo(FinalCollateralReleaseRules.nvl(collateral.getReleasedQuantity()))
              > 0) {
        throw SetupException.conflict("Non-CASH collateral remains held");
      }
    }
  }
}
