package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.client.RebateCalculationRequest;
import com.hexaware.custodian_service.client.RebateCalculationResponse;
import com.hexaware.custodian_service.client.ValuationCalculationClient;
import com.hexaware.custodian_service.client.ValuationServiceTokenProvider;
import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.entity.RebatePaymentHistory;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.repository.RebatePaymentHistoryRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashRebateAccrualService {
  private static final String ACCRUAL_EVENT = "REBATE_ACCRUAL_UPDATED";
  private static final String ACCRUAL_AGGREGATE = "CASH_REBATE_ACCRUAL";
  private static final String ACT_365 = "ACT_365";
  private static final int MAX_BACKFILL_DAYS = 3660;

  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final SettlementInstructionRepository settlements;
  private final CollateralRepository collaterals;
  private final OutboxEventRepository outbox;
  private final RebatePaymentHistoryRepository rebatePayments;
  private final ValuationCalculationClient valuation;
  private final ValuationServiceTokenProvider serviceTokens;
  private final OutgoingEventService outgoing;
  private final Clock clock;
  private final CashRebateAccrualHistory accrualHistory;
  private final PlatformTransactionManager transactionManager;

  public Map<String, Object> accrue(
      Authentication authentication,
      String transactionReference,
      LocalDate from,
      LocalDate to,
      String correctionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    FeeContractParties parties = parties(contract);
    requireCustodian(actor, parties);
    if (correctionReference != null
        && !correctionReference.matches("[A-Za-z0-9._:-]{1,80}")) {
      throw SetupException.invalid("Invalid correction reference");
    }
    return accrueRange(contract, parties, from, to, correctionReference, authentication);
  }

  public int runScheduledAccruals() {
    LocalDate through = LocalDate.now(clock).minusDays(1);
    int posted = 0;
    for (LoanContract contract :
        contracts.findByContractStatusIn(
            List.of(
                LoanContractStatus.ON_LOAN,
                LoanContractStatus.RETURNED,
                LoanContractStatus.CLOSED))) {
      try {
        FeeContractParties parties = parties(contract);
        MslaAgreement agreement = agreement(contract);
        List<SettlementInstruction> history = accrualHistory.settledHistory(contract);
        LocalDate firstTransfer = accrualHistory.firstTransferDate(history);
        LocalDate start = firstAccrualDate(agreement, firstTransfer);
        LocalDate end =
            accrualEndDate(
                agreement, accrualHistory.contractCloseDate(contract), through);
        if (start == null || start.isAfter(end)) continue;
        Map<String, Object> result =
            accrueRange(contract, parties, start, end, null, null);
        posted += ((Number) result.get("postedCount")).intValue();
      } catch (SetupException exception) {
        log.warn(
            "Cash-rebate accrual skipped for {}: {}",
            contract.getTransactionReference(),
            exception.getMessage());
      }
    }
    return posted;
  }

  public List<Map<String, Object>> rebates(
      Authentication authentication, String transactionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    requireCustodian(actor, parties(contract));
    Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
    for (OutboxEvent event : accrualHistory.acceptedEvents(transactionReference)) {
      Map<String, Object> accrual = accrualHistory.businessPayload(event);
      String itemKey = accrualHistory.text(accrual.get("itemKey"));
      Map<String, Object> group =
          grouped.computeIfAbsent(
              itemKey,
              ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("itemKey", itemKey);
                value.put("accrualDate", accrual.get("accrualDate"));
                value.put("currency", accrual.get("currency"));
                value.put("amount", BigDecimal.ZERO);
                return value;
              });
      group.put(
          "amount",
          accrualHistory
              .decimal(group.get("amount"))
              .add(accrualHistory.decimal(accrual.get("amount"))));
      group.put("cashCollateralBalance", accrual.get("settledCashCollateralAmount"));
      group.put("annualRebatePercentage", accrual.get("annualRebatePercentage"));
      group.put("latestResultReference", accrual.get("resultReference"));
      group.put("calculationResultReference", accrual.get("resultReference"));
      group.put("latestInputVersion", accrual.get("inputVersion"));
      group.put("status", accrual.get("status"));
    }
    return new ArrayList<>(grouped.values());
  }

  Map<String, Object> paymentAccrual(
      Authentication authentication, String transactionReference, String resultReference) {
    Map<String, Object> current =
        rebates(authentication, transactionReference).stream()
            .filter(row -> Objects.equals(row.get("latestResultReference"), resultReference))
            .findFirst()
            .orElseThrow(() -> SetupException.conflict("Rebate accrual result is not current"));
    LocalDate accrualDate = LocalDate.parse(String.valueOf(current.get("accrualDate")));
    OutboxEvent source =
        accrualHistory.eventsForDay(transactionReference, accrualDate).stream()
            .filter(
                event ->
                    Objects.equals(
                        accrualHistory.businessPayload(event).get("resultReference"),
                        resultReference))
            .reduce((first, second) -> second)
            .orElseThrow(() -> SetupException.conflict("Posted rebate accrual event is missing"));
    Map<String, Object> paymentBasis = new LinkedHashMap<>(current);
    paymentBasis.put("sourceEventId", source.getEventId());
    return paymentBasis;
  }

  private Map<String, Object> accrueRange(
      LoanContract contract,
      FeeContractParties parties,
      LocalDate from,
      LocalDate to,
      String correctionReference,
      Authentication authentication) {
    if (!List.of(
            LoanContractStatus.ON_LOAN,
            LoanContractStatus.RETURNED,
            LoanContractStatus.CLOSED)
        .contains(contract.getContractStatus())) {
      throw SetupException.conflict("Cash rebates accrue only for settled contracts");
    }
    if (!ACT_365.equals(contract.getDayCountBasis())) {
      throw SetupException.conflict("Only ACT_365 cash-rebate accrual is supported");
    }
    if (contract.getAgreedAnnualRebatePct() == null
        || contract.getAgreedAnnualRebatePct().signum() < 0) {
      throw SetupException.conflict("Contract has an invalid agreed annual rebate rate");
    }
    List<SettlementInstruction> history = accrualHistory.settledHistory(contract);
    List<Collateral> collateralRows = accrualHistory.collateralRows(contract);
    if (history.isEmpty() || collateralRows.isEmpty()) {
      return report(
          0,
          List.of(
              pendingDate(
                  from,
                  "Confirmed cash-collateral settlement history is missing")));
    }
    accrualHistory.validateHistory(contract, parties, history, collateralRows);
    LocalDate firstTransfer = accrualHistory.firstTransferDate(history);
    if (firstTransfer == null) return report(0, List.of());

    MslaAgreement agreement = agreement(contract);
    LocalDate closeDate = accrualHistory.contractCloseDate(contract);
    LocalDate firstAllowed = firstAccrualDate(agreement, firstTransfer);
    LocalDate lastAllowed =
        accrualEndDate(agreement, closeDate, LocalDate.now(clock).minusDays(1));
    LocalDate today = LocalDate.now(clock);
    if (from == null
        || to == null
        || from.isAfter(to)
        || from.isBefore(firstAllowed)
        || to.isAfter(lastAllowed)
        || ChronoUnit.DAYS.between(from, to) > MAX_BACKFILL_DAYS) {
      throw SetupException.invalid(
          "Rebate date range must be within completed IST days when cash collateral is held");
    }
    if (contract.getAgreedAnnualRebatePct().signum() == 0) {
      return report(0, List.of());
    }

    String token = serviceTokens.token(parties.custodianReference());
    List<Map<String, Object>> pending = new ArrayList<>();
    int posted = 0;
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      BigDecimal balance = accrualHistory.cashBalanceOnDate(history, day);
      if (balance.signum() == 0) continue;
      String inputVersion =
          accrualHistory.inputVersion(
              contract,
              history,
              day,
              balance,
              agreement.getEffectiveDate(),
              agreement.getExpiryDate(),
              closeDate);
      List<OutboxEvent> priorEvents =
          accrualHistory.eventsForDay(contract.getTransactionReference(), day);
      Map<String, Object> latest =
          priorEvents.isEmpty()
              ? null
              : accrualHistory.businessPayload(priorEvents.get(priorEvents.size() - 1));
      if (correctionReference == null
          && latest != null
          && Objects.equals(latest.get("inputVersion"), inputVersion)) {
        continue;
      }
      String supersedes =
          latest == null ? null : accrualHistory.text(latest.get("resultReference"));
      String requestReference =
          accrualHistory.requestReference(
              contract.getTransactionReference(), day, inputVersion, correctionReference);
      Instant cutoff = day.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
      RebateCalculationRequest request =
          RebateCalculationRequest.builder()
              .calculationRequestReference(requestReference)
              .transactionReference(contract.getTransactionReference())
              .accrualDate(day)
              .cutoff(cutoff)
              .settledCashCollateralAmount(balance)
              .annualRebatePercentage(contract.getAgreedAnnualRebatePct())
              .agreementEffectiveDate(agreement.getEffectiveDate())
              .agreementExpiryDate(agreement.getExpiryDate())
              .contractCloseDate(closeDate)
              .currency(contract.getSettlementCurrency())
              .dayCountBasis(contract.getDayCountBasis())
              .inputVersion(inputVersion)
              .borrowerOrganizationReference(parties.borrowerReference())
              .custodianOrganizationReference(parties.custodianReference())
              .supersedesResultReference(supersedes)
              .build();
      RebateCalculationResponse result;
      try {
        result = valuation.calculateRebate(request, token);
      } catch (SetupException exception) {
        if (exception.getStatus().value() != 503) throw exception;
        pending.add(pendingDate(day, exception.getMessage()));
        continue;
      }
      validateResult(contract, request, result);
      if (publishAccrual(contract, parties, request, result, authentication)) posted++;
    }
    return report(posted, pending);
  }

  private boolean publishAccrual(
      LoanContract original,
      FeeContractParties parties,
      RebateCalculationRequest request,
      RebateCalculationResponse result,
      Authentication authentication) {
    String eventId =
        UUID.nameUUIDFromBytes(
                (ACCRUAL_EVENT + ":" + result.getResultReference())
                    .getBytes(StandardCharsets.UTF_8))
            .toString();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    Boolean published =
        transaction.execute(
            status -> {
              LoanContract locked =
                  contracts
                      .findLockedByTransactionReference(original.getTransactionReference())
                      .orElseThrow(() -> SetupException.missing("Loan contract not found"));
              if (authentication != null) {
                Organization actor = access.actor(authentication, true);
                requireCustodian(actor, parties(locked));
              }
              MslaAgreement agreement = agreement(locked);
              LocalDate closeDate = accrualHistory.contractCloseDate(locked);
              List<SettlementInstruction> history = accrualHistory.settledHistory(locked);
              List<Collateral> collateralRows = accrualHistory.collateralRows(locked);
              accrualHistory.validateHistory(locked, parties, history, collateralRows);
              BigDecimal balance = accrualHistory.cashBalanceOnDate(
                  history, request.getAccrualDate());
              String currentInputVersion =
                  accrualHistory.inputVersion(
                      locked,
                      history,
                      request.getAccrualDate(),
                      balance,
                      agreement.getEffectiveDate(),
                      agreement.getExpiryDate(),
                      closeDate);
              if (!Objects.equals(request.getInputVersion(), currentInputVersion)
                  || !Objects.equals(
                      locked.getAgreedAnnualRebatePct(), original.getAgreedAnnualRebatePct())
                  || !Objects.equals(
                      locked.getSettlementCurrency(), original.getSettlementCurrency())) {
                return false;
              }
              if (outbox.existsByEventId(eventId)) return false;
              List<OutboxEvent> prior =
                  accrualHistory.eventsForDay(
                      locked.getTransactionReference(), request.getAccrualDate());
              String currentSupersedes =
                  prior.isEmpty()
                      ? null
                      : accrualHistory.text(
                          accrualHistory
                              .businessPayload(prior.get(prior.size() - 1))
                              .get("resultReference"));
              if (!Objects.equals(
                  currentSupersedes, request.getSupersedesResultReference())) {
                return false;
              }
              BigDecimal oldAccrued =
                  prior.stream()
                      .map(
                          event ->
                              accrualHistory.decimal(
                                  accrualHistory.businessPayload(event).get("amount")))
                      .reduce(BigDecimal.ZERO, BigDecimal::add);
              BigDecimal adjustment = result.getAmount().subtract(oldAccrued);
              if (adjustment.signum() == 0) return false;
              if (!prior.isEmpty()
                  && rebatePayments.findLockedByContractId(locked.getContractId()).stream()
                      .anyMatch(
                          payment ->
                              Objects.equals(
                                  payment.getObligationReference(),
                                  "REBATE:" + request.getAccrualDate())
                                  && ("PENDING".equals(payment.getStatus())
                                      || "PAID".equals(payment.getStatus())))) {
                throw SetupException.conflict(
                    "A rebate accrual cannot be corrected while its payment is pending or paid");
              }

              long aggregateVersion =
                  accrualHistory.nextAggregateVersion(locked.getTransactionReference());
              Map<String, Object> payload = new LinkedHashMap<>();
              payload.put("payloadVersion", 1);
              payload.put("transactionReference", locked.getTransactionReference());
              payload.put("borrowerOrganizationReference", parties.borrowerReference());
              payload.put("custodianOrganizationReference", parties.custodianReference());
              payload.put("resultReference", result.getResultReference());
              payload.put("supersedesResultReference", request.getSupersedesResultReference());
              payload.put("itemKey", "REBATE:" + request.getAccrualDate());
              payload.put("accrualDate", request.getAccrualDate().toString());
              payload.put(
                  "settledCashCollateralAmount",
                  request.getSettledCashCollateralAmount().toPlainString());
              payload.put(
                  "annualRebatePercentage", request.getAnnualRebatePercentage().toPlainString());
              payload.put("grossAmount", result.getAmount().toPlainString());
              payload.put("amount", adjustment.toPlainString());
              payload.put("currency", locked.getSettlementCurrency());
              payload.put("dayCountBasis", request.getDayCountBasis());
              payload.put("accrualBasis", result.getAccrualBasis());
              payload.put("inputVersion", request.getInputVersion());
              payload.put("calculationRuleVersion", result.getCalculationRuleVersion());
              payload.put("status", "POSTED");
              payload.put("aggregateVersion", aggregateVersion);
              payload.put("updatedAt", clock.instant().toString());
              outgoing.enqueue(
                  ACCRUAL_AGGREGATE,
                  EventEnvelope.builder()
                      .eventId(eventId)
                      .eventType(ACCRUAL_EVENT)
                      .schemaVersion(1)
                      .producerService("CUSTODIAN")
                      .destinationService("BORROWER")
                      .correlationId(locked.getTransactionReference())
                      .aggregateReference(locked.getTransactionReference())
                      .aggregateVersion(aggregateVersion)
                      .occurredAt(clock.instant())
                      .senderOrganizationReference(parties.custodianReference())
                      .receiverOrganizationReference(parties.borrowerReference())
                      .payload(payload)
                      .build());
              return true;
            });
    return Boolean.TRUE.equals(published);
  }

  private void validateResult(
      LoanContract contract,
      RebateCalculationRequest request,
      RebateCalculationResponse response) {
    if (!"COMPLETED".equals(response.getStatus())
        || response.getResultReference() == null
        || !Objects.equals(
            response.getCalculationRequestReference(),
            request.getCalculationRequestReference())
        || !Objects.equals(response.getTransactionReference(), contract.getTransactionReference())
        || !Objects.equals(response.getAccrualDate(), request.getAccrualDate())
        || !Objects.equals(response.getCutoff(), request.getCutoff())
        || response.getSettledCashCollateralAmount() == null
        || response
                .getSettledCashCollateralAmount()
                .compareTo(request.getSettledCashCollateralAmount())
            != 0
        || response.getAnnualRebatePercentage() == null
        || response.getAnnualRebatePercentage().compareTo(request.getAnnualRebatePercentage()) != 0
        || !Objects.equals(response.getCurrency(), contract.getSettlementCurrency())
        || !Objects.equals(response.getDayCountBasis(), request.getDayCountBasis())
        || !"SETTLED_CASH_COLLATERAL".equals(response.getAccrualBasis())
        || !Objects.equals(response.getInputVersion(), request.getInputVersion())
        || response.getAmount() == null
        || response.getAmount().signum() < 0
        || response.getAmount().scale() > 2
        || !Objects.equals(
            response.getSupersedesResultReference(), request.getSupersedesResultReference())) {
      throw SetupException.conflict("Valuation result does not match the rebate accrual inputs");
    }
  }

  private LocalDate firstAccrualDate(MslaAgreement agreement, LocalDate firstTransfer) {
    if (firstTransfer == null) return null;
    return firstTransfer.isBefore(agreement.getEffectiveDate())
        ? agreement.getEffectiveDate()
        : firstTransfer;
  }

  private LocalDate accrualEndDate(
      MslaAgreement agreement, LocalDate closeDate, LocalDate completedThrough) {
    LocalDate end = completedThrough;
    if (agreement.getExpiryDate() != null && agreement.getExpiryDate().isBefore(end)) {
      end = agreement.getExpiryDate();
    }
    if (closeDate != null && closeDate.isBefore(end)) end = closeDate;
    return end;
  }

  private Map<String, Object> report(int postedCount, List<Map<String, Object>> pending) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("postedCount", postedCount);
    result.put("pending", pending);
    return result;
  }

  private Map<String, Object> pendingDate(LocalDate date, String reason) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("date", date.toString());
    result.put("status", "PENDING");
    result.put("reason", reason);
    return result;
  }

  private MslaAgreement agreement(LoanContract contract) {
    LoanRequest request =
        requests
            .findById(contract.getLoanRequestId())
            .orElseThrow(() -> SetupException.missing("Loan request not found"));
    return agreements
        .findById(request.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
  }

  FeeContractParties parties(LoanContract contract) {
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
    Organization lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .orElseThrow(() -> SetupException.missing("Lender organization not found"));
    Organization custodian =
        organizations
            .findById(mandate.getCustodianOrgId())
            .orElseThrow(() -> SetupException.missing("Custodian organization not found"));
    return new FeeContractParties(
        borrower.getOrganizationId(),
        borrower.getOrganizationReference(),
        lender.getOrganizationId(),
        lender.getOrganizationReference(),
        custodian.getOrganizationId(),
        custodian.getOrganizationReference());
  }

  private LoanContract contract(String transactionReference) {
    return contracts
        .findByTransactionReference(transactionReference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private void requireCustodian(Organization actor, FeeContractParties parties) {
    if (!Objects.equals(actor.getOrganizationId(), parties.custodianId())) {
      throw SetupException.missing("Loan contract not found in this Custodian scope");
    }
  }
}
