package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.client.LendingFeeCalculationRequest;
import com.hexaware.custodian_service.client.LendingFeeCalculationResponse;
import com.hexaware.custodian_service.client.ValuationCalculationClient;
import com.hexaware.custodian_service.client.ValuationServiceTokenProvider;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LendingFeePayment;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LendingFeePaymentRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class LendingFeeAccrualService {
  private static final String CHARGE_EVENT = "LENDING_FEE_CHARGE_UPDATED";
  private static final String CHARGE_AGGREGATE = "LENDING_FEE_CHARGE";
  private static final String ACT_365 = "ACT_365";
  private static final int MAX_BACKFILL_DAYS = 3660;

  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final SettlementInstructionRepository settlements;
  private final LendingFeePaymentRepository feePayments;
  private final OutboxEventRepository outbox;
  private final ValuationCalculationClient valuation;
  private final ValuationServiceTokenProvider serviceTokens;
  private final OutgoingEventService outgoing;
  private final Clock clock;
  private final LendingFeeAccrualHistory accrualHistory;
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
    LocalDate through = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1);
    int posted = 0;
    for (LoanContract contract : contracts.findByContractStatusIn(
        List.of(LoanContractStatus.ON_LOAN, LoanContractStatus.RETURNED))) {
      try {
        FeeContractParties parties = parties(contract);
        LocalDate start = accrualHistory.firstDeliveryDate(contract);
        if (start == null || start.isAfter(through)) continue;
        Map<String, Object> result =
            accrueRange(contract, parties, start, through, null, null);
        posted += ((Number) result.get("postedCount")).intValue();
      } catch (SetupException exception) {
        log.warn("Lending-fee accrual skipped for {}: {}",
            contract.getTransactionReference(), exception.getMessage());
      }
    }
    return posted;
  }

  public List<Map<String, Object>> charges(
      Authentication authentication, String transactionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contract(transactionReference);
    requireCustodian(actor, parties(contract));
    Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
    for (OutboxEvent event : accrualHistory.acceptedChargeEvents(transactionReference)) {
      Map<String, Object> charge = event.getPayload();
      String itemKey = accrualHistory.text(charge.get("itemKey"));
      Map<String, Object> group = grouped.computeIfAbsent(itemKey, ignored -> {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("itemKey", itemKey);
        value.put("accrualDate", charge.get("accrualDate"));
        value.put("currency", charge.get("currency"));
        value.put("amount", BigDecimal.ZERO);
        return value;
      });
      group.put(
          "amount",
          accrualHistory
              .decimal(group.get("amount"))
              .add(accrualHistory.decimal(charge.get("amount"))));
      group.put("latestResultReference", charge.get("resultReference"));
      group.put("latestInputVersion", charge.get("inputVersion"));
    }
    List<LendingFeePayment> contractPayments =
        feePayments.findByContractIdOrderByPaymentId(contract.getContractId());
    for (Map<String, Object> group : grouped.values()) {
      BigDecimal paid = BigDecimal.ZERO;
      BigDecimal pending = BigDecimal.ZERO;
      for (LendingFeePayment payment : contractPayments) {
        if (!Objects.equals(
            payment.getCalculationSnapshot().get("chargeItemKey"), group.get("itemKey"))) {
          continue;
        }
        if ("PAID".equals(payment.getStatus())) paid = paid.add(payment.getPaymentAmount());
        if ("PENDING".equals(payment.getStatus())) {
          pending = pending.add(payment.getPaymentAmount());
        }
      }
      BigDecimal amount = accrualHistory.decimal(group.get("amount"));
      group.put("paidAmount", paid);
      group.put("pendingAmount", pending);
      group.put("outstandingAmount", amount.subtract(paid).max(BigDecimal.ZERO));
      group.put("availableAmount",
          amount.subtract(paid).subtract(pending).max(BigDecimal.ZERO));
      group.put("creditAmount", paid.subtract(amount).max(BigDecimal.ZERO));
    }
    return new ArrayList<>(grouped.values());
  }

  public List<Map<String, Object>> chargeEvents(String transactionReference) {
    return accrualHistory.acceptedChargeEvents(transactionReference).stream()
        .map(OutboxEvent::getPayload)
        .toList();
  }

  private Map<String, Object> accrueRange(
      LoanContract contract,
      FeeContractParties parties,
      LocalDate from,
      LocalDate to,
      String correctionReference,
      Authentication authentication) {
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    LocalDate firstDelivery = accrualHistory.firstDeliveryDate(contract);
    if (from == null || to == null || from.isAfter(to)
        || to.isAfter(today.minusDays(1))
        || from.isBefore(firstDelivery == null ? from : firstDelivery)
        || java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_BACKFILL_DAYS) {
      throw SetupException.invalid("Accrual date range must contain completed UTC days");
    }
    if (!ACT_365.equals(contract.getDayCountBasis())) {
      throw SetupException.conflict("Only the approved ACT_365 fee basis is supported");
    }
    if (contract.getContractStatus() != LoanContractStatus.ON_LOAN
        && contract.getContractStatus() != LoanContractStatus.RETURNED) {
      throw SetupException.conflict("Fees accrue only for settled contracts");
    }

    List<SettlementInstruction> settledHistory = accrualHistory.settledHistory(contract);
    if (settledHistory.isEmpty() || firstDelivery == null) {
      return report(0, List.of(Map.of(
          "date", from.toString(), "reason", "Confirmed delivery settlement history is missing")));
    }
    accrualHistory.validateHistory(contract, parties, settledHistory);
    String token = serviceTokens.token(parties.custodianReference());
    List<Map<String, Object>> pending = new ArrayList<>();
    int posted = 0;
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      long quantity = accrualHistory.accrualQuantityOnDate(settledHistory, day);
      if (quantity == 0) continue;
      String inputVersion = accrualHistory.inputVersion(contract, settledHistory, day, quantity);
      List<OutboxEvent> priorEvents =
          accrualHistory.eventsForDay(contract.getTransactionReference(), day);
      Map<String, Object> latest = priorEvents.isEmpty()
          ? null
          : priorEvents.get(priorEvents.size() - 1).getPayload();
      if (correctionReference == null
          && latest != null
          && Objects.equals(latest.get("inputVersion"), inputVersion)) {
        continue;
      }
      String supersedes =
          latest == null ? null : accrualHistory.text(latest.get("resultReference"));
      String requestReference = accrualHistory.requestReference(
          contract.getTransactionReference(), day, inputVersion, correctionReference);
      Instant cutoff = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      LendingFeeCalculationRequest request = LendingFeeCalculationRequest.builder()
          .calculationRequestReference(requestReference)
          .transactionReference(contract.getTransactionReference())
          .securityIsin(requests.findById(contract.getLoanRequestId())
              .orElseThrow(() -> SetupException.missing("Loan request not found"))
              .getSecurityIsin())
          .accrualDate(day)
          .cutoff(cutoff)
          .settledOutstandingQuantity(quantity)
          .annualFeePercentage(contract.getAgreedAnnualFeePct())
          .currency(contract.getSettlementCurrency())
          .dayCountBasis(contract.getDayCountBasis())
          .inputVersion(inputVersion)
          .borrowerOrganizationReference(parties.borrowerReference())
          .custodianOrganizationReference(parties.custodianReference())
          .supersedesResultReference(supersedes)
          .build();
      LendingFeeCalculationResponse result;
      try {
        result = valuation.calculateLendingFee(request, token);
      } catch (SetupException exception) {
        if (exception.getStatus().value() != 503) throw exception;
        pending.add(pendingDate(day, exception.getMessage()));
        continue;
      }
      if ("PENDING".equals(result.getStatus())) {
        pending.add(pendingDate(day, result.getPendingReason()));
        continue;
      }
      accrualHistory.validateResult(contract, request, result);
      if (publishCharge(contract, parties, request, result, authentication)) posted++;
    }
    return report(posted, pending);
  }

  private boolean publishCharge(
      LoanContract original,
      FeeContractParties parties,
      LendingFeeCalculationRequest request,
      LendingFeeCalculationResponse result,
      Authentication authentication) {
    String eventId = UUID.nameUUIDFromBytes(
        (CHARGE_EVENT + ":" + result.getResultReference()).getBytes(StandardCharsets.UTF_8))
        .toString();
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    Boolean published = transaction.execute(status -> {
      LoanContract locked = contracts.findLockedByTransactionReference(
              original.getTransactionReference())
          .orElseThrow(() -> SetupException.missing("Loan contract not found"));
      if (authentication != null) {
        Organization actor = access.actor(authentication, true);
        requireCustodian(actor, parties(locked));
      }
      if (!accrualHistory.sameFeeInputs(original, locked)
          || !Objects.equals(request.getInputVersion(),
              accrualHistory.inputVersion(
                  locked,
                  accrualHistory.settledHistory(locked),
                  request.getAccrualDate(),
                  accrualHistory.accrualQuantityOnDate(
                      accrualHistory.settledHistory(locked), request.getAccrualDate())))) {
        return false;
      }
      if (outbox.existsByEventId(eventId)) return false;
      List<OutboxEvent> prior = accrualHistory.eventsForDay(
          locked.getTransactionReference(), request.getAccrualDate());
      String currentSupersedes = prior.isEmpty()
          ? null
          : accrualHistory.text(prior.get(prior.size() - 1).getPayload().get("resultReference"));
      if (!Objects.equals(currentSupersedes, request.getSupersedesResultReference())) {
        return false;
      }
      BigDecimal oldAccrued = prior.stream()
          .map(event -> accrualHistory.decimal(event.getPayload().get("amount")))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal adjustment = result.getAmount().subtract(oldAccrued);
      if (adjustment.signum() == 0) return false;
      long aggregateVersion =
          accrualHistory.acceptedChargeEvents(locked.getTransactionReference()).size() + 1L;
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("payloadVersion", 1);
      payload.put("transactionReference", locked.getTransactionReference());
      payload.put("borrowerOrganizationReference", parties.borrowerReference());
      payload.put("custodianOrganizationReference", parties.custodianReference());
      payload.put("resultReference", result.getResultReference());
      payload.put("supersedesResultReference", request.getSupersedesResultReference());
      payload.put("itemKey", "LENDING_FEE:" + request.getAccrualDate());
      payload.put("accrualDate", request.getAccrualDate().toString());
      payload.put("settledOutstandingQuantity", request.getSettledOutstandingQuantity());
      payload.put("marketPrice", result.getMarketPrice().toPlainString());
      payload.put("priceReference", result.getPriceReference());
      payload.put("priceSource", result.getPriceSource());
      payload.put("priceTimestamp", result.getPriceTimestamp().toString());
      payload.put("annualFeePercentage", request.getAnnualFeePercentage().toPlainString());
      payload.put("grossAmount", result.getAmount().toPlainString());
      payload.put("amount", adjustment.toPlainString());
      payload.put("currency", locked.getSettlementCurrency());
      payload.put("dayCountBasis", request.getDayCountBasis());
      payload.put("feeBasis", result.getFeeBasis());
      payload.put("inputVersion", request.getInputVersion());
      payload.put("calculationRuleVersion", result.getCalculationRuleVersion());
      payload.put("status", "POSTED");
      payload.put("aggregateVersion", aggregateVersion);
      payload.put("updatedAt", clock.instant().toString());
      outgoing.enqueue(CHARGE_AGGREGATE, EventEnvelope.builder()
          .eventId(eventId)
          .eventType(CHARGE_EVENT)
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

  private FeeContractParties parties(LoanContract contract) {
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
    return new FeeContractParties(
        borrower.getOrganizationId(),
        borrower.getOrganizationReference(),
        lender.getOrganizationId(),
        lender.getOrganizationReference(),
        custodian.getOrganizationId(),
        custodian.getOrganizationReference());
  }

  private LoanContract contract(String transactionReference) {
    return contracts.findByTransactionReference(transactionReference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private void requireCustodian(Organization actor, FeeContractParties parties) {
    if (!Objects.equals(actor.getOrganizationId(), parties.custodianId())) {
      throw SetupException.missing("Loan contract not found in this Custodian scope");
    }
  }

}
