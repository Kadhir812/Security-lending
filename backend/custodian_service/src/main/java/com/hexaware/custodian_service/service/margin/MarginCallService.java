package com.hexaware.custodian_service.service.margin;

import com.hexaware.custodian_service.client.MtmCalculationRequest;
import com.hexaware.custodian_service.client.MtmCalculationResponse;
import com.hexaware.custodian_service.client.ValuationCalculationClient;
import com.hexaware.custodian_service.client.ValuationServiceTokenProvider;
import com.hexaware.custodian_service.dto.response.MarginCallResponse;
import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.CreditExposureHistory;
import com.hexaware.custodian_service.entity.CounterpartyCreditLimit;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MarginCall;
import com.hexaware.custodian_service.entity.MarginCallTransactionLog;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.MarginCallStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.CreditExposureHistoryRepository;
import com.hexaware.custodian_service.repository.CounterpartyCreditLimitRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MarginCallRepository;
import com.hexaware.custodian_service.repository.MarginCallTransactionLogRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarginCallService {
  private static final List<MarginCallStatus> ACTIVE_STATUSES =
      List.of(
          MarginCallStatus.OPEN,
          MarginCallStatus.PARTIALLY_SATISFIED,
          MarginCallStatus.BREACHED);
  private static final List<MarginCallStatus> DUE_STATUSES =
      List.of(MarginCallStatus.OPEN, MarginCallStatus.PARTIALLY_SATISFIED);

  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final SettlementInstructionRepository settlements;
  private final CollateralRepository collaterals;
  private final MarginCallRepository calls;
  private final MarginCallTransactionLogRepository callLogs;
  private final CounterpartyCreditLimitRepository creditLimits;
  private final CreditExposureHistoryRepository exposureHistory;
  private final ValuationCalculationClient mtm;
  private final ValuationServiceTokenProvider serviceTokens;
  private final OutgoingEventService outgoing;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;

  public MarginCallResponse triggerMtm(
      Authentication authentication, String transactionReference, String idempotencyKey) {
    if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,80}")) {
      throw SetupException.invalid("A valid Idempotency-Key header is required for MTM");
    }
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    ContractParty parties = parties(contract);
    requireCustodian(actor, parties);
    return calculateAndApply(
        contract, parties, idempotencyKey, authentication, clock.instant());
  }

  public MarginCallResponse evaluate(Authentication authentication, String transactionReference) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    return transaction.execute(
        status -> {
          Organization lockedActor = access.actor(authentication, true);
          LoanContract locked = lockedContract(transactionReference);
          ContractParty parties = parties(locked);
          requireCustodian(lockedActor, parties);
          if (locked.getContractStatus() != LoanContractStatus.ON_LOAN
              || locked.getLatestValuationResultReference() == null) {
            throw SetupException.conflict("An applied MTM valuation is required before evaluation");
          }
          return evaluateCall(locked, parties);
        });
  }

  public MtmCalculationResponse calculateReturnValuation(
      LoanContract contract, long remainingQuantity, String calculationReference) {
    if (remainingQuantity < 0 || remainingQuantity > contract.getOutstandingQuantity()
        || contract.getVersion() == null || contract.getVersion() <= 0) {
      throw SetupException.conflict("Return valuation inputs are invalid or stale");
    }
    LoanRequest request = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    ContractParty parties = parties(contract);
    BigDecimal currentCash = confirmedCash(contract.getContractId());
    MtmCalculationRequest calculation = MtmCalculationRequest.builder()
        .securityIsin(request.getSecurityIsin())
        .calculationRequestReference(calculationReference)
        .outstandingQuantity(remainingQuantity)
        .coveragePercentage(contract.getCollateralCoveragePct())
        .confirmedCashCollateral(currentCash)
        .currency(contract.getSettlementCurrency())
        .custodianOrganizationReference(parties.custodianOrganizationReference())
        .borrowerOrganizationReference(parties.borrowerOrganizationReference())
        .transactionReference(contract.getTransactionReference())
        .inputAsOf(clock.instant())
        .inputVersion(contract.getVersion())
        .build();
    MtmCalculationResponse result =
        mtm.calculateMtm(calculation, serviceTokens.token(parties.custodianOrganizationReference()));
    validateResult(calculation, result);
    return result;
  }

  public void applyReturnValuation(
      LoanContract contract,
      MtmCalculationResponse result,
      String idempotencyKey,
      Long settlementInstructionId,
      long remainingQuantity) {
    LoanRequest request = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    ContractParty parties = parties(contract);
    BigDecimal cash = confirmedCash(contract.getContractId());
    if (contract.getOutstandingQuantity() == null
        || contract.getOutstandingQuantity() <= remainingQuantity
        || contract.getVersion() == null
        || !Objects.equals(contract.getVersion(), result.getInputVersion())
        || !Objects.equals(result.getTransactionReference(), contract.getTransactionReference())
        || !Objects.equals(result.getSecurityIsin(), request.getSecurityIsin())
        || !Objects.equals(result.getCurrency(), contract.getSettlementCurrency())
        || !Objects.equals(result.getOutstandingQuantity(), remainingQuantity)
        || result.getCoveragePercentage() == null
        || result.getCoveragePercentage().compareTo(contract.getCollateralCoveragePct()) != 0
        || result.getConfirmedCashCollateral() == null
        || result.getConfirmedCashCollateral().compareTo(cash) != 0
        || !Objects.equals(result.getCustodianOrganizationReference(),
            parties.custodianOrganizationReference())
        || !Objects.equals(result.getBorrowerOrganizationReference(),
            parties.borrowerOrganizationReference())
        || result.getCalculatedAt() == null
        || (contract.getLatestValuationAt() != null
            && !result.getCalculatedAt().isAfter(contract.getLatestValuationAt()))) {
      throw SetupException.conflict("Return valuation is stale or does not match the confirmed return");
    }
    String inputHash = returnInputHash(
        contract, request, remainingQuantity, cash, result.getInputAsOf());
    if (exposureHistory.findByContractIdAndIdempotencyKey(contract.getContractId(), idempotencyKey)
        .isPresent()) {
      throw SetupException.conflict("Return exposure update was already applied");
    }
    applyExposureDelta(contract, result, idempotencyKey, inputHash,
        "Confirmed securities return exposure delta for " + contract.getTransactionReference());
    contract.setLatestValuationResultReference(result.getCalculationReference());
    contract.setLatestMarketPrice(result.getMarketPrice());
    contract.setLatestLoanValue(result.getLoanMarketValue());
    contract.setLatestRequiredCollateral(result.getRequiredCashCollateral());
    contract.setLatestCoveragePct(result.getCoveragePercentage());
    contract.setLatestAdjustedCollateral(result.getConfirmedCashCollateral());
    contract.setLatestValuationAt(result.getCalculatedAt());
    contracts.save(contract);
    evaluateCall(contract, parties);
  }

  public MarginCallResponse get(
      Authentication authentication, String transactionReference, String callReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    requireCustodian(actor, parties(contract));
    MarginCall call =
        calls.findByMarginCallReference(callReference)
            .orElseThrow(() -> SetupException.missing("Margin call not found"));
    if (!Objects.equals(call.getContractId(), contract.getContractId())) {
      throw SetupException.missing("Margin call not found in this contract");
    }
    return response(call, contract);
  }

  public List<MarginCallResponse> list(
      Authentication authentication, String transactionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    requireCustodian(actor, parties(contract));
    return calls.findByContractIdOrderByCreatedAtDesc(contract.getContractId()).stream()
        .map(call -> response(call, contract))
        .toList();
  }

  public int runScheduledMtm() {
    Instant now = clock.instant();
    int applied = 0;
    for (LoanContract contract : contracts.findByContractStatus(LoanContractStatus.ON_LOAN)) {
      ContractParty parties = parties(contract);
      MslaAgreement agreement = agreementFor(contract);
      Instant prior = contract.getLatestValuationAt() == null
          ? contract.getContractCreatedAt()
          : contract.getLatestValuationAt();
      Instant dueAt = prior.plus(agreement.getMtmIntervalMinutes(), ChronoUnit.MINUTES);
      if (dueAt.isAfter(now)) continue;
      long intervalSeconds = agreement.getMtmIntervalMinutes() * 60L;
      long slot = now.getEpochSecond() / intervalSeconds;
      String key = UUID.nameUUIDFromBytes(
          (contract.getTransactionReference() + ":MTM:" + slot)
              .getBytes(StandardCharsets.UTF_8)).toString();
      Instant inputAsOf = Instant.ofEpochSecond(slot * intervalSeconds);
      try {
        calculateAndApply(contract, parties, key, null, inputAsOf);
        applied++;
      } catch (SetupException exception) {
        log.warn("Scheduled MTM skipped for {}: {}", contract.getTransactionReference(),
            exception.getMessage());
      }
    }
    return applied;
  }

  public int markOverdueCalls() {
    int changed = 0;
    Instant now = clock.instant();
    for (Long callId : calls.findDueIds(DUE_STATUSES, now)) {
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);
      Boolean didChange = transaction.execute(status -> {
        MarginCall beforeLock = calls.findById(callId)
            .orElseThrow(() -> SetupException.missing("Margin call not found"));
        LoanContract contract = contracts.findLockedByTransactionReference(
                contracts.findById(beforeLock.getContractId())
                    .orElseThrow(() -> SetupException.missing("Loan contract not found"))
                    .getTransactionReference())
            .orElseThrow(() -> SetupException.missing("Loan contract not found"));
        MarginCall call = calls.findLockedById(callId)
            .orElseThrow(() -> SetupException.missing("Margin call not found"));
        if (!ACTIVE_STATUSES.contains(call.getStatus()) || call.getDueAt().isAfter(now)) {
          return false;
        }
        call.setStatus(MarginCallStatus.BREACHED);
        calls.saveAndFlush(call);
        ContractParty parties = parties(contract);
        publishCall(call, contract, parties, "BREACHED", null, "Margin call deadline passed", "BREACHED");
        return true;
      });
      if (Boolean.TRUE.equals(didChange)) changed++;
    }
    return changed;
  }

  public MarginCallResponse recordTopUp(
      LoanContract contract, MarginCall call, Long settlementInstructionId) {
    if (call.getStatus() == MarginCallStatus.CANCELLED
        || call.getStatus() == MarginCallStatus.FAILED) {
      throw SetupException.conflict("This margin call does not accept top-ups");
    }
    BigDecimal current = confirmedCash(contract.getContractId());
    BigDecimal required = nvl(contract.getLatestRequiredCollateral());
    call.setCurrentCollateral(current);
    call.setRequiredCollateral(required);
    call.setStatus(current.compareTo(required) >= 0
        ? MarginCallStatus.SATISFIED
        : call.getStatus() == MarginCallStatus.BREACHED
            ? MarginCallStatus.BREACHED
            : current.signum() > 0
                ? MarginCallStatus.PARTIALLY_SATISFIED
                : MarginCallStatus.OPEN);
    if (call.getStatus() == MarginCallStatus.SATISFIED) {
      call.setResolvedAt(clock.instant());
    }
    contract.setLatestAdjustedCollateral(current);
    contracts.save(contract);
    calls.saveAndFlush(call);
    publishCall(call, contract, parties(contract), "TOP_UP_CONFIRMED",
        settlementInstructionId, "Simulated CASH receipt confirmed",
        call.getStatus() == MarginCallStatus.SATISFIED ? "SATISFIED" : "TOP_UP_CONFIRMED");
    return response(call, contract);
  }

  private MarginCallResponse calculateAndApply(
      LoanContract input, ContractParty parties, String idempotencyKey,
      Authentication authentication, Instant inputAsOf) {
    if (input.getContractStatus() != LoanContractStatus.ON_LOAN
        || input.getOutstandingQuantity() == null
        || input.getOutstandingQuantity() <= 0) {
      throw SetupException.conflict("Only settled loans with outstanding quantity can be valued");
    }
    LoanRequest request = requests.findById(input.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    BigDecimal current = confirmedCash(input.getContractId());
    String inputHash = inputHash(input, request, current);
    var previous =
        exposureHistory.findByContractIdAndIdempotencyKey(input.getContractId(), idempotencyKey);
    if (previous.isPresent()) {
      if (!inputHash.equals(previous.get().getInputHash())) {
        throw SetupException.conflict("MTM idempotency key was reused with changed inputs");
      }
      return calls.findByValuationResultReference(previous.get().getValuationResultReference())
          .map(call -> response(call, input))
          .orElse(null);
    }
    MtmCalculationRequest calculation = MtmCalculationRequest.builder()
        .securityIsin(request.getSecurityIsin())
        .calculationRequestReference(idempotencyKey)
        .outstandingQuantity(input.getOutstandingQuantity())
        .coveragePercentage(input.getCollateralCoveragePct())
        .confirmedCashCollateral(current)
        .currency(input.getSettlementCurrency())
        .custodianOrganizationReference(parties.custodianOrganizationReference())
        .borrowerOrganizationReference(parties.borrowerOrganizationReference())
        .transactionReference(input.getTransactionReference())
        .inputAsOf(inputAsOf)
        .inputVersion(input.getVersion())
        .build();
    MtmCalculationResponse result =
        mtm.calculateMtm(calculation, serviceTokens.token(parties.custodianOrganizationReference()));
    validateResult(calculation, result);

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    MarginCallResponse applied;
    try {
      applied = transaction.execute(status -> {
      LoanContract locked = lockedContract(input.getTransactionReference());
      ContractParty currentParties = parties(locked);
      if (authentication != null) {
        Organization actor = access.actor(authentication, true);
        requireCustodian(actor, currentParties);
      }
      if (!sameParties(parties, currentParties)
          || !Objects.equals(locked.getVersion(), result.getInputVersion())
          || !Objects.equals(locked.getOutstandingQuantity(), result.getOutstandingQuantity())
          || locked.getContractStatus() != LoanContractStatus.ON_LOAN
          || confirmedCash(locked.getContractId()).compareTo(result.getConfirmedCashCollateral()) != 0
          || (locked.getLatestValuationAt() != null
              && !result.getCalculatedAt().isAfter(locked.getLatestValuationAt()))) {
        throw SetupException.conflict("Contract inputs changed or the MTM result is stale");
      }
      applyExposureDelta(locked, result, idempotencyKey, inputHash);
      locked.setLatestValuationResultReference(result.getCalculationReference());
      locked.setLatestMarketPrice(result.getMarketPrice());
      locked.setLatestLoanValue(result.getLoanMarketValue());
      locked.setLatestRequiredCollateral(result.getRequiredCashCollateral());
      locked.setLatestCoveragePct(result.getCoveragePercentage());
      locked.setLatestAdjustedCollateral(result.getConfirmedCashCollateral());
      locked.setLatestValuationAt(result.getCalculatedAt());
      contracts.save(locked);
      return evaluateCall(locked, currentParties);
      });
    } catch (DataIntegrityViolationException duplicate) {
      var saved = exposureHistory.findByContractIdAndIdempotencyKey(
          input.getContractId(), idempotencyKey);
      if (saved.isPresent() && inputHash.equals(saved.get().getInputHash())) {
        return calls.findByValuationResultReference(saved.get().getValuationResultReference())
            .map(call -> response(call, input))
            .orElse(null);
      }
      throw SetupException.conflict("Concurrent MTM update conflicted with a committed valuation");
    }
    if (applied == null) {
      return null;
    }
    return applied;
  }

  private void applyExposureDelta(
      LoanContract contract, MtmCalculationResponse result, String idempotencyKey,
      String inputHash) {
    applyExposureDelta(contract, result, idempotencyKey, inputHash,
        "MTM exposure delta for contract " + contract.getTransactionReference());
  }

  private void applyExposureDelta(
      LoanContract contract, MtmCalculationResponse result, String idempotencyKey,
      String inputHash, String reason) {
    LoanRequest request = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    CounterpartyCreditLimit credit = creditLimits.findLockedByMslaId(request.getMslaId())
        .orElseThrow(() -> SetupException.conflict("Counterparty credit limit not found"));
    BigDecimal oldContractExposure = nvl(contract.getUsedCreditAmount());
    BigDecimal oldAggregateExposure = nvl(credit.getUsedAmount());
    BigDecimal newContractExposure = result.getLoanMarketValue();
    BigDecimal newAggregateExposure =
        oldAggregateExposure.add(newContractExposure.subtract(oldContractExposure));
    if (newAggregateExposure.signum() < 0) {
      throw SetupException.conflict("Credit exposure cannot become negative");
    }
    BigDecimal oldAvailable = credit.getLimitAmount()
        .subtract(oldAggregateExposure)
        .subtract(nvl(credit.getReservedAmount()));
    BigDecimal newAvailable = credit.getLimitAmount()
        .subtract(newAggregateExposure)
        .subtract(nvl(credit.getReservedAmount()));
    credit.setUsedAmount(newAggregateExposure);
    contract.setUsedCreditAmount(newContractExposure);
    creditLimits.save(credit);
    exposureHistory.save(CreditExposureHistory.builder()
        .creditLimitId(credit.getCreditLimitId())
        .contractId(contract.getContractId())
        .valuationResultReference(result.getCalculationReference())
        .idempotencyKey(idempotencyKey)
        .inputHash(inputHash)
        .oldUsedAmount(oldAggregateExposure)
        .newUsedAmount(newAggregateExposure)
        .oldAvailableAmount(oldAvailable)
        .newAvailableAmount(newAvailable)
        .breachCreated(oldAvailable.signum() >= 0 && newAvailable.signum() < 0)
        .reason(reason)
        .changedAt(clock.instant())
        .build());
  }

  private MarginCallResponse evaluateCall(LoanContract contract, ContractParty parties) {
    BigDecimal required = nvl(contract.getLatestRequiredCollateral());
    if (contract.getLatestValuationResultReference() == null || required.signum() < 0) {
      throw SetupException.conflict("Applied MTM result is required to evaluate collateral");
    }
    BigDecimal current = confirmedCash(contract.getContractId());
    BigDecimal shortfall = required.subtract(current).max(BigDecimal.ZERO);
    List<MarginCall> active = calls.findActiveLocked(contract.getContractId(), ACTIVE_STATUSES);
    Instant now = clock.instant();
    if (shortfall.signum() == 0) {
      for (MarginCall call : active) {
        if (call.getStatus() != MarginCallStatus.SATISFIED
            || call.getCurrentCollateral().compareTo(current) != 0) {
          call.setCurrentCollateral(current);
          call.setRequiredCollateral(required);
          call.setStatus(MarginCallStatus.SATISFIED);
          call.setResolvedAt(now);
          calls.saveAndFlush(call);
          callLogs.save(log(call, "SATISFIED", null, null, "Collateral requirement satisfied"));
          publishCall(call, contract, parties, "SATISFIED", null,
              "Collateral requirement satisfied", "SATISFIED");
        }
      }
      return active.isEmpty() ? null : response(active.get(0), contract);
    }

    MarginCall call;
    boolean created = active.isEmpty();
    if (created) {
      MslaAgreement agreement = agreementFor(contract);
      Instant dueAt = now.plus(agreement.getMarginCallWindowHours(), ChronoUnit.HOURS);
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("approvedCollateralTerms", contract.getCollateralTerms());
      snapshot.put("valuationResultReference", contract.getLatestValuationResultReference());
      snapshot.put("marketPrice", contract.getLatestMarketPrice());
      snapshot.put("loanMarketValue", contract.getLatestLoanValue());
      snapshot.put("coveragePercentage", contract.getLatestCoveragePct());
      snapshot.put("calculatedAt", contract.getLatestValuationAt().toString());
      call = MarginCall.builder()
          .marginCallReference(UUID.randomUUID().toString())
          .contractId(contract.getContractId())
          .valuationResultReference(contract.getLatestValuationResultReference())
          .requiredCollateral(required)
          .currentCollateral(current)
          .status(current.signum() == 0
              ? MarginCallStatus.OPEN
              : MarginCallStatus.PARTIALLY_SATISFIED)
          .createdAt(now)
          .dueAt(dueAt)
          .calculationSnapshot(snapshot)
          .build();
    } else {
      call = active.get(0);
      call.setRequiredCollateral(required);
      call.setCurrentCollateral(current);
      call.setValuationResultReference(contract.getLatestValuationResultReference());
      if (call.getStatus() != MarginCallStatus.BREACHED) {
        call.setStatus(current.signum() == 0
            ? MarginCallStatus.OPEN
            : MarginCallStatus.PARTIALLY_SATISFIED);
        call.setResolvedAt(null);
      }
      Map<String, Object> snapshot = new LinkedHashMap<>(call.getCalculationSnapshot());
      Map<String, Object> latestValuation = new LinkedHashMap<>();
      latestValuation.put("valuationResultReference", contract.getLatestValuationResultReference());
      latestValuation.put("marketPrice", contract.getLatestMarketPrice());
      latestValuation.put("loanMarketValue", contract.getLatestLoanValue());
      latestValuation.put("requiredCollateral", required);
      latestValuation.put("confirmedCashCollateral", current);
      latestValuation.put("calculatedAt", contract.getLatestValuationAt().toString());
      snapshot.put("latestValuation", latestValuation);
      call.setCalculationSnapshot(snapshot);
    }
    calls.saveAndFlush(call);
    publishCall(call, contract, parties, created ? "ISSUED" : "VALUATION_UPDATED",
        null, created ? "Margin call issued" : "Margin call updated by MTM valuation",
        created ? "ISSUED" : "NOTE");
    return response(call, contract);
  }

  private void publishCall(
      MarginCall call, LoanContract contract, ContractParty parties, String reason,
      Long settlementInstructionId, String notes, String activityType) {
    String eventId = UUID.randomUUID().toString();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", contract.getTransactionReference());
    payload.put("marginCallReference", call.getMarginCallReference());
    payload.put("borrowerOrganizationReference", parties.borrowerOrganizationReference());
    payload.put("custodianOrganizationReference", parties.custodianOrganizationReference());
    payload.put("status", call.getStatus().name());
    payload.put("requiredCollateral", call.getRequiredCollateral().toPlainString());
    payload.put("currentCollateral", call.getCurrentCollateral().toPlainString());
    payload.put("outstandingAmount", call.getRequiredCollateral()
        .subtract(call.getCurrentCollateral()).max(BigDecimal.ZERO).toPlainString());
    payload.put("dueAt", call.getDueAt().toString());
    payload.put("valuationResultReference", call.getValuationResultReference());
    payload.put("updatedAt", clock.instant().toString());
    payload.put("aggregateVersion", call.getVersion() == null ? 0L : call.getVersion());
    payload.put("reason", reason);
    payload.put("settlementInstructionId", settlementInstructionId);
    payload.put("notes", notes);
    if (settlementInstructionId != null) {
      SettlementInstruction instruction = settlements.findById(settlementInstructionId)
          .orElseThrow(() -> SetupException.missing("Top-up settlement instruction not found"));
      payload.put("instructionReference", instruction.getInstructionReference());
      payload.put("settlementStatus", instruction.getStatus().name());
      payload.put("settlementAmount", instruction.getAmount().toPlainString());
      payload.put("settlementCurrency", instruction.getCurrency());
      payload.put("externalConfirmationReference", instruction.getExternalConfirmationReference());
      payload.put("simulatedConfirmation", true);
    }
    outgoing.enqueue("MARGIN_CALL", EventEnvelope.builder()
        .eventId(eventId)
        .eventType("MARGIN_CALL_UPDATED")
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(call.getMarginCallReference())
        .aggregateReference(call.getMarginCallReference())
        .aggregateVersion(call.getVersion() == null ? 0L : call.getVersion())
        .occurredAt(clock.instant())
        .senderOrganizationReference(parties.custodianOrganizationReference())
        .receiverOrganizationReference(parties.borrowerOrganizationReference())
        .payload(payload)
        .build());
    callLogs.save(log(call, activityType, settlementInstructionId, eventId, notes));
  }

  private MarginCallTransactionLog log(
      MarginCall call, String activity, Long settlementInstructionId,
      String eventId, String notes) {
    BigDecimal amount = settlementInstructionId == null
        ? call.getRequiredCollateral().subtract(call.getCurrentCollateral()).max(BigDecimal.ZERO)
        : settlements.findById(settlementInstructionId)
            .map(SettlementInstruction::getAmount)
            .orElseThrow(() -> SetupException.missing("Top-up settlement instruction not found"));
    return MarginCallTransactionLog.builder()
        .marginCallId(call.getMarginCallId())
        .settlementInstructionId(settlementInstructionId)
        .activityType(activity)
        .amount(amount)
        .sourceEventId(eventId)
        .notes(notes)
        .activityTimestamp(clock.instant())
        .build();
  }

  private void validateResult(MtmCalculationRequest request, MtmCalculationResponse result) {
    if (result == null
        || !Objects.equals(request.getCalculationRequestReference(), result.getCalculationRequestReference())
        || !Objects.equals(request.getTransactionReference(), result.getTransactionReference())
        || !Objects.equals(request.getSecurityIsin(), result.getSecurityIsin())
        || !Objects.equals(request.getCurrency(), result.getCurrency())
        || !Objects.equals(request.getOutstandingQuantity(), result.getOutstandingQuantity())
        || request.getCoveragePercentage().compareTo(result.getCoveragePercentage()) != 0
        || request.getConfirmedCashCollateral().compareTo(result.getConfirmedCashCollateral()) != 0
        || !Objects.equals(request.getInputAsOf(), result.getInputAsOf())
        || !Objects.equals(request.getInputVersion(), result.getInputVersion())
        || !Objects.equals(request.getCustodianOrganizationReference(), result.getCustodianOrganizationReference())
        || !Objects.equals(request.getBorrowerOrganizationReference(), result.getBorrowerOrganizationReference())
        || result.getCashHaircutPercentage() == null
        || result.getCashHaircutPercentage().signum() != 0
        || result.getCalculationReference() == null
        || result.getCalculatedAt() == null
        || result.getPriceTimestamp() == null
        || result.getCalculatedAt().isBefore(result.getPriceTimestamp())
        || result.getCalculatedAt().isBefore(request.getInputAsOf())
        || result.getMarketPrice() == null
        || result.getMarketPrice().signum() <= 0
        || result.getLoanMarketValue() == null
        || result.getRequiredCashCollateral() == null
        || result.getShortfallAmount() == null) {
      throw SetupException.conflict("MTM result is inconsistent with approved contract inputs");
    }
  }

  private MarginCallResponse response(MarginCall call, LoanContract contract) {
    BigDecimal outstanding =
        call.getRequiredCollateral().subtract(call.getCurrentCollateral()).max(BigDecimal.ZERO);
    return new MarginCallResponse(
        call.getMarginCallReference(),
        contract.getTransactionReference(),
        call.getValuationResultReference(),
        call.getRequiredCollateral(),
        call.getCurrentCollateral(),
        outstanding,
        outstanding,
        call.getStatus().name(),
        call.getCreatedAt(),
        call.getDueAt(),
        call.getResolvedAt(),
        call.getCalculationSnapshot());
  }

  private BigDecimal confirmedCash(Long contractId) {
    return collaterals.findByContractId(contractId).stream()
        .filter(c -> "POSTED".equals(c.getStatus())
            && c.getCollateralType() != null
            && "CASH".equals(c.getCollateralType().name()))
        .map(Collateral::getPostedCashAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private LoanContract contract(String reference) {
    return contracts.findByTransactionReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private LoanContract lockedContract(String reference) {
    return contracts.findLockedByTransactionReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private ContractParty parties(LoanContract contract) {
    LoanRequest request = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement = agreements.findById(request.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    Organization borrower = organizations.findById(agreement.getBorrowerOrgId())
        .orElseThrow(() -> SetupException.missing("Borrower organization not found"));
    Organization custodian = organizations.findById(mandate.getCustodianOrgId())
        .orElseThrow(() -> SetupException.missing("Custodian organization not found"));
    return new ContractParty(
        borrower.getOrganizationId(),
        borrower.getOrganizationReference(),
        custodian.getOrganizationId(),
        custodian.getOrganizationReference());
  }

  private MslaAgreement agreementFor(LoanContract contract) {
    LoanRequest request = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    return agreements.findById(request.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
  }

  private void requireCustodian(Organization actor, ContractParty parties) {
    if (!actor.getOrganizationId().equals(parties.custodianOrganizationId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Contract is outside the authenticated Custodian mandate");
    }
  }

  private boolean sameParties(ContractParty expected, ContractParty actual) {
    return expected.borrowerOrganizationId().equals(actual.borrowerOrganizationId())
        && expected.custodianOrganizationId().equals(actual.custodianOrganizationId());
  }

  private static BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String inputHash(LoanContract contract, LoanRequest request, BigDecimal cash) {
    String canonical = String.join("|",
        contract.getTransactionReference(),
        request.getSecurityIsin(),
        String.valueOf(contract.getOutstandingQuantity()),
        contract.getCollateralCoveragePct().stripTrailingZeros().toPlainString(),
        cash.stripTrailingZeros().toPlainString(),
        contract.getSettlementCurrency());
    try {
      return java.util.HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime", exception);
    }
  }

  private String returnInputHash(
      LoanContract contract,
      LoanRequest request,
      long remainingQuantity,
      BigDecimal cash,
      Instant inputAsOf) {
    String canonical = String.join("|",
        contract.getTransactionReference(),
        request.getSecurityIsin(),
        String.valueOf(remainingQuantity),
        contract.getCollateralCoveragePct().stripTrailingZeros().toPlainString(),
        cash.stripTrailingZeros().toPlainString(),
        contract.getSettlementCurrency(),
        inputAsOf.toString());
    try {
      return java.util.HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime", exception);
    }
  }

  private record ContractParty(
      Long borrowerOrganizationId,
      String borrowerOrganizationReference,
      Long custodianOrganizationId,
      String custodianOrganizationReference) {}
}
