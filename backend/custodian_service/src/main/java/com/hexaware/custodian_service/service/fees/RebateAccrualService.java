package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.client.RebateCalculationRequest;
import com.hexaware.custodian_service.client.RebateCalculationResponse;
import com.hexaware.custodian_service.client.ValuationCalculationClient;
import com.hexaware.custodian_service.client.ValuationServiceTokenProvider;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class RebateAccrualService {
  private static final String EVENT_TYPE = "REBATE_CHARGE_UPDATED";
  private static final String AGGREGATE_TYPE = "REBATE_CHARGE";
  private static final int MAX_BACKFILL_DAYS = 3660;

  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final SettlementInstructionRepository settlements;
  private final OutboxEventRepository outbox;
  private final ValuationCalculationClient valuation;
  private final ValuationServiceTokenProvider serviceTokens;
  private final OutgoingEventService outgoing;
  private final Clock clock;
  private final PlatformTransactionManager transactionManager;

  public Map<String, Object> accrue(
      Authentication authentication, String transactionReference, LocalDate from, LocalDate to) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    Parties parties = parties(contract);
    requireCustodian(actor, parties);
    return accrueRange(contract, parties, from, to, authentication);
  }

  public int runScheduledAccruals() {
    LocalDate through = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1);
    int posted = 0;
    for (LoanContract contract : contracts.findByContractStatusIn(
        List.of(LoanContractStatus.ON_LOAN, LoanContractStatus.RETURNED))) {
      try {
        boolean releaseSettled = settlements.findByContractId(contract.getContractId()).stream()
            .anyMatch(row -> "COLLATERAL_RELEASE".equals(row.getInstructionType())
                && row.getStatus() == SettlementInstructionStatus.SETTLED);
        if (releaseSettled) continue;
        Parties parties = parties(contract);
        LocalDate start = firstDeliveryDate(contract);
        if (start == null || start.isAfter(through)) continue;
        Map<String, Object> result = accrueRange(contract, parties, start, through, null);
        posted += ((Number) result.get("postedCount")).intValue();
      } catch (SetupException exception) {
        log.warn("Cash-collateral rebate accrual skipped for {}: {}",
            contract.getTransactionReference(), exception.getMessage());
      }
    }
    return posted;
  }

  /** Finalize all completed UTC rebate days and return their net accrued obligation. */
  public BigDecimal accrueThroughLastCompletedDay(LoanContract contract) {
    Parties parties = parties(contract);
    LocalDate through = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1);
    LocalDate start = firstDeliveryDate(contract);
    if (start != null && !start.isAfter(through)) {
      Map<String, Object> result = accrueRange(contract, parties, start, through, null);
      if (!((List<?>) result.get("pending")).isEmpty()) {
        throw SetupException.unavailable("Final rebate accrual is pending Valuation service recovery");
      }
    }
    return acceptedEvents(contract.getTransactionReference()).stream()
        .map(this::businessPayload)
        .map(row -> decimal(row.get("amount")))
        .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
        .setScale(2);
  }

  public List<Map<String, Object>> charges(Authentication authentication, String transactionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    Parties parties = parties(contract);
    requireCustodian(actor, parties);
    return acceptedEvents(transactionReference).stream().map(this::businessPayload).toList();
  }

  private Map<String, Object> accrueRange(
      LoanContract original, Parties parties, LocalDate from, LocalDate to,
      Authentication authentication) {
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    LocalDate start = firstDeliveryDate(original);
    if (from == null || to == null || from.isAfter(to) || to.isAfter(today.minusDays(1))
        || (start != null && from.isBefore(start))
        || java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_BACKFILL_DAYS) {
      throw SetupException.invalid("Rebate date range must contain completed UTC days after first delivery");
    }
    if (!"ACT_365".equals(original.getDayCountBasis())) {
      throw SetupException.conflict("Only ACT_365 cash-collateral rebate accrual is supported");
    }
    if (original.getContractStatus() != LoanContractStatus.ON_LOAN
        && original.getContractStatus() != LoanContractStatus.RETURNED) {
      throw SetupException.conflict("Rebates accrue only for settled loan contracts");
    }
    boolean releaseSettled = settlements.findByContractId(original.getContractId()).stream()
        .anyMatch(row -> "COLLATERAL_RELEASE".equals(row.getInstructionType())
            && row.getStatus() == SettlementInstructionStatus.SETTLED);
    if (releaseSettled) {
      throw SetupException.conflict("Rebate accrual is finalized with the first confirmed collateral release");
    }
    if (original.getAgreedAnnualRebatePct() == null
        || original.getAgreedAnnualRebatePct().signum() < 0) {
      throw SetupException.conflict("Approved contract rebate rate is invalid");
    }

    List<SettlementInstruction> history = settledHistory(original);
    validateCollateralHistory(original, parties, history);
    String token = serviceTokens.token(parties.custodianReference());
    List<Map<String, Object>> pending = new ArrayList<>();
    int posted = 0;
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      List<OutboxEvent> previous = eventsForDay(original.getTransactionReference(), day);
      BigDecimal collateral = collateralOnDate(history, original, day);
      if (collateral.signum() == 0 && previous.isEmpty()) continue;
      String inputVersion = inputVersion(original, history, parties, day, collateral);
      Map<String, Object> previousPayload = previous.isEmpty()
          ? null : businessPayload(previous.get(previous.size() - 1));
      if (previousPayload != null && Objects.equals(previousPayload.get("inputVersion"), inputVersion)) {
        continue;
      }
      String supersedes = previousPayload == null ? null : text(previousPayload.get("resultReference"));
      RebateCalculationRequest request = RebateCalculationRequest.builder()
          .calculationRequestReference(requestReference(original.getTransactionReference(), day, inputVersion))
          .transactionReference(original.getTransactionReference())
          .accrualDate(day)
          .cutoff(day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
          .settledCashCollateral(collateral)
          .annualRebatePercentage(original.getAgreedAnnualRebatePct())
          .currency(original.getSettlementCurrency())
          .dayCountBasis(original.getDayCountBasis())
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
        pending.add(pending(day, exception.getMessage()));
        continue;
      }
      validateResult(request, result);
      if (publishCharge(original, parties, request, result, previous, authentication)) posted++;
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("postedCount", posted);
    result.put("pending", pending);
    return result;
  }

  private boolean publishCharge(
      LoanContract original, Parties parties, RebateCalculationRequest request,
      RebateCalculationResponse calculation, List<OutboxEvent> previous,
      Authentication authentication) {
    String eventId = UUID.nameUUIDFromBytes(
        (EVENT_TYPE + ":" + calculation.getResultReference()).getBytes(StandardCharsets.UTF_8))
        .toString();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    Boolean published = transaction.execute(status -> {
      LoanContract locked = contracts.findLockedByTransactionReference(original.getTransactionReference())
          .orElseThrow(() -> SetupException.missing("Loan contract not found"));
      if (authentication != null) {
        requireCustodian(access.actor(authentication, true), parties(locked));
      }
      List<SettlementInstruction> currentHistory = settledHistory(locked);
      validateCollateralHistory(locked, parties, currentHistory);
      BigDecimal currentCollateral = collateralOnDate(currentHistory, locked, request.getAccrualDate());
      if (!Objects.equals(request.getInputVersion(),
          inputVersion(locked, currentHistory, parties, request.getAccrualDate(), currentCollateral))) {
        return false;
      }
      if (outbox.existsByEventId(eventId)) return false;
      List<OutboxEvent> currentEvents = eventsForDay(locked.getTransactionReference(), request.getAccrualDate());
      String currentSupersedes = currentEvents.isEmpty() ? null
          : text(businessPayload(currentEvents.get(currentEvents.size() - 1)).get("resultReference"));
      if (!Objects.equals(currentSupersedes, request.getSupersedesResultReference())) return false;
      BigDecimal accrued = currentEvents.stream()
          .map(row -> decimal(businessPayload(row).get("amount")))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal adjustment = calculation.getAmount().subtract(accrued).setScale(2);
      if (adjustment.signum() == 0) return false;

      long version = nextAggregateVersion(locked.getTransactionReference());
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("payloadVersion", 1);
      payload.put("transactionReference", locked.getTransactionReference());
      payload.put("borrowerOrganizationReference", parties.borrowerReference());
      payload.put("custodianOrganizationReference", parties.custodianReference());
      payload.put("resultReference", calculation.getResultReference());
      payload.put("supersedesResultReference", request.getSupersedesResultReference());
      payload.put("itemKey", "REBATE:" + request.getAccrualDate());
      payload.put("accrualDate", request.getAccrualDate().toString());
      payload.put("settledCashCollateral", currentCollateral.toPlainString());
      payload.put("annualRebatePercentage", request.getAnnualRebatePercentage().toPlainString());
      payload.put("grossAmount", calculation.getAmount().toPlainString());
      payload.put("amount", adjustment.toPlainString());
      payload.put("currency", locked.getSettlementCurrency());
      payload.put("dayCountBasis", request.getDayCountBasis());
      payload.put("rebateBasis", calculation.getRebateBasis());
      payload.put("inputVersion", request.getInputVersion());
      payload.put("calculationRuleVersion", calculation.getCalculationRuleVersion());
      payload.put("status", "POSTED");
      payload.put("aggregateVersion", version);
      payload.put("updatedAt", clock.instant().toString());
      outgoing.enqueue(AGGREGATE_TYPE, EventEnvelope.builder()
          .eventId(eventId)
          .eventType(EVENT_TYPE)
          .schemaVersion(1)
          .producerService("CUSTODIAN")
          .destinationService("BORROWER")
          .correlationId(locked.getTransactionReference())
          .aggregateReference(locked.getTransactionReference())
          .aggregateVersion(version)
          .occurredAt(clock.instant())
          .senderOrganizationReference(parties.custodianReference())
          .receiverOrganizationReference(parties.borrowerReference())
          .payload(payload)
          .build());
      return true;
    });
    return Boolean.TRUE.equals(published);
  }

  private BigDecimal collateralOnDate(
      List<SettlementInstruction> history, LoanContract contract, LocalDate day) {
    BigDecimal balance = BigDecimal.ZERO.setScale(2);
    for (SettlementInstruction row : history) {
      if (row.getSettledAt() == null || !contract.getSettlementCurrency().equals(row.getCurrency())) continue;
      LocalDate settled = row.getSettledAt().atZone(ZoneOffset.UTC).toLocalDate();
      if ("COLLATERAL_TRANSFER".equals(row.getInstructionType())
          && !settled.isAfter(day)) {
        balance = balance.add(row.getAmount());
      } else if ("COLLATERAL_RELEASE".equals(row.getInstructionType())
          && settled.isBefore(day)) {
        balance = balance.subtract(row.getAmount());
      }
      if (balance.signum() < 0) throw SetupException.conflict("Settled collateral release exceeds posted cash");
    }
    return balance.setScale(2);
  }

  private void validateCollateralHistory(LoanContract contract, Parties parties,
      List<SettlementInstruction> history) {
    for (SettlementInstruction row : history) {
      if (!"COLLATERAL_TRANSFER".equals(row.getInstructionType())
          && !"COLLATERAL_RELEASE".equals(row.getInstructionType())) continue;
      boolean transfer = "COLLATERAL_TRANSFER".equals(row.getInstructionType());
      Long expectedFrom = transfer ? parties.borrowerId() : parties.custodianId();
      Long expectedTo = transfer ? parties.custodianId() : parties.borrowerId();
      if (!Objects.equals(row.getContractId(), contract.getContractId())
          || !Objects.equals(row.getFromOrganizationId(), expectedFrom)
          || !Objects.equals(row.getToOrganizationId(), expectedTo)
          || row.getAmount() == null || row.getAmount().signum() <= 0 || row.getAmount().scale() > 2
          || row.getSettledAt() == null || !contract.getSettlementCurrency().equals(row.getCurrency())) {
        throw SetupException.conflict("Settled CASH collateral history does not match contract parties or currency");
      }
    }
  }

  private List<SettlementInstruction> settledHistory(LoanContract contract) {
    return settlements.findByContractIdAndStatusOrderBySettledAtAsc(
        contract.getContractId(), SettlementInstructionStatus.SETTLED);
  }

  private LocalDate firstDeliveryDate(LoanContract contract) {
    return settledHistory(contract).stream()
        .filter(row -> "SECURITY_DELIVERY".equals(row.getInstructionType()))
        .map(SettlementInstruction::getSettledAt)
        .filter(Objects::nonNull)
        .map(value -> value.atZone(ZoneOffset.UTC).toLocalDate())
        .min(LocalDate::compareTo).orElse(null);
  }

  private String inputVersion(LoanContract contract, List<SettlementInstruction> history,
      Parties parties, LocalDate day, BigDecimal collateral) {
    StringBuilder value = new StringBuilder()
        .append(contract.getTransactionReference()).append('|').append(day).append('|')
        .append(collateral.toPlainString()).append('|')
        .append(contract.getAgreedAnnualRebatePct().toPlainString()).append('|')
        .append(contract.getSettlementCurrency()).append('|').append(contract.getDayCountBasis())
        .append('|').append(parties.lenderReference()).append('|').append(parties.borrowerReference());
    Instant cutoff = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    history.stream()
        .filter(row -> row.getSettledAt() != null && row.getSettledAt().isBefore(cutoff))
        .filter(row -> "COLLATERAL_TRANSFER".equals(row.getInstructionType())
            || "COLLATERAL_RELEASE".equals(row.getInstructionType()))
        .forEach(row -> value.append('|').append(row.getInstructionId()).append(':')
            .append(row.getVersion()).append(':').append(row.getInstructionType()).append(':')
            .append(row.getAmount().toPlainString()).append(':').append(row.getSettledAt()));
    return hash(value.toString());
  }

  private List<OutboxEvent> acceptedEvents(String transactionReference) {
    List<OutboxEvent> candidates = outbox
        .findByEventTypeAndAggregateTypeAndAggregateReferenceOrderByAggregateVersionAscCreatedAtAsc(
            EVENT_TYPE, AGGREGATE_TYPE, transactionReference)
        .stream()
        .filter(row -> Objects.equals(businessPayload(row).get("transactionReference"), transactionReference))
        .toList();
    Map<String, OutboxEvent> latestByItem = new LinkedHashMap<>();
    List<OutboxEvent> accepted = new ArrayList<>();
    for (OutboxEvent event : candidates) {
      Map<String, Object> payload = businessPayload(event);
      String itemKey = text(payload.get("itemKey"));
      OutboxEvent previous = latestByItem.get(itemKey);
      String priorResult = previous == null ? null : text(businessPayload(previous).get("resultReference"));
      if (!Objects.equals(text(payload.get("supersedesResultReference")), priorResult)) continue;
      accepted.add(event);
      latestByItem.put(itemKey, event);
    }
    return accepted;
  }

  private List<OutboxEvent> eventsForDay(String transactionReference, LocalDate day) {
    return acceptedEvents(transactionReference).stream()
        .filter(row -> Objects.equals(businessPayload(row).get("accrualDate"), day.toString()))
        .toList();
  }

  private Map<String, Object> businessPayload(OutboxEvent event) {
    Object content = event.getPayload().get("payload");
    if (!(content instanceof Map<?, ?> map)) throw SetupException.conflict("Rebate charge event payload is invalid");
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, value) -> { if (key instanceof String name) result.put(name, value); });
    return result;
  }

  private long nextAggregateVersion(String transactionReference) {
    try {
      return Math.addExact(outbox.findMaxAggregateVersion(EVENT_TYPE, AGGREGATE_TYPE, transactionReference), 1L);
    } catch (ArithmeticException exception) {
      throw SetupException.conflict("Rebate charge aggregate version is exhausted");
    }
  }

  private void validateResult(RebateCalculationRequest request, RebateCalculationResponse response) {
    if (response == null || !"COMPLETED".equals(response.getStatus())
        || response.getResultReference() == null
        || !Objects.equals(request.getCalculationRequestReference(), response.getCalculationRequestReference())
        || !Objects.equals(request.getTransactionReference(), response.getTransactionReference())
        || !Objects.equals(request.getAccrualDate(), response.getAccrualDate())
        || !Objects.equals(request.getCutoff(), response.getCutoff())
        || response.getSettledCashCollateral() == null
        || request.getSettledCashCollateral().compareTo(response.getSettledCashCollateral()) != 0
        || response.getAnnualRebatePercentage() == null
        || request.getAnnualRebatePercentage().compareTo(response.getAnnualRebatePercentage()) != 0
        || !Objects.equals(request.getCurrency(), response.getCurrency())
        || !Objects.equals(request.getDayCountBasis(), response.getDayCountBasis())
        || !Objects.equals(request.getInputVersion(), response.getInputVersion())
        || !Objects.equals(request.getSupersedesResultReference(), response.getSupersedesResultReference())
        || !"CASH-COLLATERAL-REBATE-ACT365-UTC-V1".equals(response.getCalculationRuleVersion())
        || response.getAmount() == null || response.getAmount().signum() < 0
        || response.getAmount().scale() > 2
        || !"DAILY_SETTLED_CASH_COLLATERAL".equals(response.getRebateBasis())) {
      throw SetupException.conflict("Valuation rebate result does not match settled collateral inputs");
    }
  }

  private Parties parties(LoanContract contract) {
    LoanRequest request = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement = agreements.findById(request.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    Organization borrower = organizations.findById(agreement.getBorrowerOrgId())
        .orElseThrow(() -> SetupException.missing("Borrower organization not found"));
    Organization lender = organizations.findById(mandate.getLenderOrgId())
        .orElseThrow(() -> SetupException.missing("Lender organization not found"));
    Organization custodian = organizations.findById(mandate.getCustodianOrgId())
        .orElseThrow(() -> SetupException.missing("Custodian organization not found"));
    return new Parties(borrower.getOrganizationId(), lender.getOrganizationId(), custodian.getOrganizationId(),
        borrower.getOrganizationReference(), lender.getOrganizationReference(), custodian.getOrganizationReference());
  }

  private void requireCustodian(Organization actor, Parties parties) {
    if (!Objects.equals(actor.getOrganizationId(), parties.custodianId())) {
      throw new AccessDeniedException("Contract is outside the authenticated Custodian mandate");
    }
  }

  private LoanContract contract(String reference) {
    if (reference == null || !reference.matches("[A-Za-z0-9._:-]{1,80}")) {
      throw SetupException.invalid("Invalid transaction reference");
    }
    return contracts.findByTransactionReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private String requestReference(String transactionReference, LocalDate day, String inputVersion) {
    return UUID.nameUUIDFromBytes((transactionReference + ":REBATE:" + day + ":" + inputVersion)
        .getBytes(StandardCharsets.UTF_8)).toString();
  }

  private Map<String, Object> pending(LocalDate day, String reason) {
    return Map.of("date", day.toString(), "status", "PENDING", "reason", reason);
  }

  private BigDecimal decimal(Object value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private String hash(String value) {
    try {
      return java.util.HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime", exception);
    }
  }

  private record Parties(Long borrowerId, Long lenderId, Long custodianId,
      String borrowerReference, String lenderReference, String custodianReference) {}
}
