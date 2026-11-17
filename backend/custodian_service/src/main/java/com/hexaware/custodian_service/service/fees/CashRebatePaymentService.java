package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.dto.request.CreateRebatePaymentRequest;
import com.hexaware.custodian_service.dto.request.RebatePaymentConfirmationRequest;
import com.hexaware.custodian_service.dto.request.RebatePaymentFailureRequest;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.RebatePaymentHistory;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.RebatePaymentHistoryRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashRebatePaymentService {
  private static final String PAYMENT_EVENT = "REBATE_PAYMENT_UPDATED";
  private static final List<LoanContractStatus> PAYABLE_CONTRACT_STATUSES =
      List.of(
          LoanContractStatus.ON_LOAN,
          LoanContractStatus.RETURNED,
          LoanContractStatus.CLOSED);

  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final RebatePaymentHistoryRepository payments;
  private final CashRebateAccrualService accruals;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  @Transactional
  public Map<String, Object> create(
      Authentication authentication,
      String transactionReference,
      CreateRebatePaymentRequest request) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FeeContractParties parties = accruals.parties(contract);
    requireCustodian(actor, parties);
    requirePayableContract(contract);

    Map<String, Object> accrual =
        accruals.paymentAccrual(
            authentication, transactionReference, request.getCalculationResultReference());
    String itemKey = text(accrual.get("itemKey"));
    String currency = text(accrual.get("currency"));
    BigDecimal amount = decimal(accrual.get("amount"));
    if (itemKey == null
        || !itemKey.matches("REBATE:[0-9]{4}-[0-9]{2}-[0-9]{2}")
        || !Objects.equals(accrual.get("status"), "POSTED")
        || !Objects.equals(currency, contract.getSettlementCurrency())
        || amount.signum() <= 0
        || amount.scale() > 2) {
      throw SetupException.conflict("Rebate accrual has no positive payable balance");
    }

    List<RebatePaymentHistory> existing = payments.findLockedByContractId(contract.getContractId());
    RebatePaymentHistory sameReference =
        existing.stream()
            .filter(row -> request.getPaymentReference().equals(row.getPaymentReference()))
            .findFirst()
            .orElseGet(
                () ->
                    payments
                        .findByPaymentReference(request.getPaymentReference())
                        .orElse(null));
    if (sameReference != null) {
      if (Objects.equals(sameReference.getContractId(), contract.getContractId())
          && Objects.equals(
              sameReference.getCalculationResultReference(),
              request.getCalculationResultReference())
          && sameReference.getPaymentAmount().compareTo(amount) == 0) {
        return response(contract, sameReference);
      }
      throw SetupException.conflict("Rebate payment reference was reused");
    }
    if (existing.stream()
        .anyMatch(
            row ->
                Objects.equals(row.getObligationReference(), itemKey)
                    && ("PENDING".equals(row.getStatus()) || "PAID".equals(row.getStatus())))) {
      throw SetupException.conflict(
          "This rebate accrual already has a pending or completed payout");
    }

    String eventId = eventId(request.getPaymentReference(), 1);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("paymentReference", request.getPaymentReference());
    snapshot.put("transactionReference", transactionReference);
    snapshot.put("itemKey", itemKey);
    snapshot.put("accrualDate", accrual.get("accrualDate"));
    snapshot.put("calculationResultReference", request.getCalculationResultReference());
    snapshot.put("amount", amount.toPlainString());
    snapshot.put("currency", currency);
    snapshot.put("borrowerOrganizationReference", parties.borrowerReference());
    snapshot.put("lenderOrganizationReference", parties.lenderReference());
    snapshot.put("custodianOrganizationReference", parties.custodianReference());
    snapshot.put("sourceAccrualEventId", accrual.get("sourceEventId"));
    snapshot.put("createdEventId", eventId);
    snapshot.put("aggregateVersion", 1);
    snapshot.put("createdAt", clock.instant().toString());

    RebatePaymentHistory payment =
        RebatePaymentHistory.builder()
            .contractId(contract.getContractId())
            .obligationReference(itemKey)
            .calculationSnapshot(snapshot)
            .paymentReference(request.getPaymentReference())
            .calculationResultReference(request.getCalculationResultReference())
            .fromOrganizationId(parties.lenderId())
            .toOrganizationId(parties.borrowerId())
            .paymentAmount(amount)
            .currency(currency)
            .status("PENDING")
            .build();
    try {
      payments.saveAndFlush(payment);
    } catch (DataIntegrityViolationException exception) {
      throw SetupException.conflict("Rebate payment reference or obligation was already recorded");
    }
    enqueue(contract, parties, payment, "PENDING", 1, null, null, eventId,
        String.valueOf(accrual.get("sourceEventId")));
    return response(contract, payment);
  }

  @Transactional
  public Map<String, Object> confirm(
      Authentication authentication,
      String transactionReference,
      String paymentReference,
      RebatePaymentConfirmationRequest request) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FeeContractParties parties = accruals.parties(contract);
    requireCustodian(actor, parties);
    RebatePaymentHistory payment = lockedPayment(contract, paymentReference);
    String externalReference = request.getExternalConfirmationReference();
    if ("PAID".equals(payment.getStatus())) {
      if (Objects.equals(payment.getExternalReference(), externalReference)) {
        return response(contract, payment);
      }
      throw SetupException.conflict("Rebate payment already has a different confirmation");
    }
    if (!"PENDING".equals(payment.getStatus())) {
      throw SetupException.conflict("Only a pending rebate payment can be confirmed");
    }
    payments
        .findByExternalReference(externalReference)
        .filter(existing -> !Objects.equals(existing.getPaymentId(), payment.getPaymentId()))
        .ifPresent(
            existing -> {
              throw SetupException.conflict("External confirmation reference was reused");
            });

    payment.setStatus("PAID");
    payment.setPaidAt(clock.instant());
    payment.setExternalReference(externalReference);
    Map<String, Object> snapshot = new LinkedHashMap<>(payment.getCalculationSnapshot());
    snapshot.put("aggregateVersion", 2);
    snapshot.put("externalConfirmationReference", externalReference);
    snapshot.put("confirmedAt", clock.instant().toString());
    payment.setCalculationSnapshot(snapshot);
    try {
      payments.saveAndFlush(payment);
    } catch (DataIntegrityViolationException exception) {
      throw SetupException.conflict("External confirmation reference was reused");
    }
    enqueue(
        contract,
        parties,
        payment,
        "PAID",
        2,
        externalReference,
        null,
        eventId(paymentReference, 2),
        text(snapshot.get("createdEventId")));
    return response(contract, payment);
  }

  @Transactional
  public Map<String, Object> fail(
      Authentication authentication,
      String transactionReference,
      String paymentReference,
      RebatePaymentFailureRequest request) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FeeContractParties parties = accruals.parties(contract);
    requireCustodian(actor, parties);
    RebatePaymentHistory payment = lockedPayment(contract, paymentReference);
    if ("FAILED".equals(payment.getStatus())
        && Objects.equals(payment.getFailureReason(), request.getReason())) {
      return response(contract, payment);
    }
    if (!"PENDING".equals(payment.getStatus())) {
      throw SetupException.conflict("Only a pending rebate payment can be marked failed");
    }

    payment.setStatus("FAILED");
    payment.setFailureReason(request.getReason());
    Map<String, Object> snapshot = new LinkedHashMap<>(payment.getCalculationSnapshot());
    snapshot.put("aggregateVersion", 2);
    snapshot.put("failureReason", request.getReason());
    snapshot.put("failedAt", clock.instant().toString());
    payment.setCalculationSnapshot(snapshot);
    payments.saveAndFlush(payment);
    enqueue(
        contract,
        parties,
        payment,
        "FAILED",
        2,
        null,
        request.getReason(),
        eventId(paymentReference, 2),
        text(snapshot.get("createdEventId")));
    return response(contract, payment);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(
      Authentication authentication, String transactionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract =
        contracts
            .findByTransactionReference(transactionReference)
            .orElseThrow(() -> SetupException.missing("Loan contract not found"));
    requireCustodian(actor, accruals.parties(contract));
    return payments.findByContractIdOrderByPaymentId(contract.getContractId()).stream()
        .map(payment -> response(contract, payment))
        .toList();
  }

  private void enqueue(
      LoanContract contract,
      FeeContractParties parties,
      RebatePaymentHistory payment,
      String status,
      long aggregateVersion,
      String externalReference,
      String failureReason,
      String eventId,
      String causationEventId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", contract.getTransactionReference());
    payload.put("paymentReference", payment.getPaymentReference());
    payload.put("obligationReference", payment.getObligationReference());
    payload.put("itemKey", payment.getObligationReference());
    payload.put("accrualDate", payment.getCalculationSnapshot().get("accrualDate"));
    payload.put("calculationResultReference", payment.getCalculationResultReference());
    payload.put("borrowerOrganizationReference", parties.borrowerReference());
    payload.put("lenderOrganizationReference", parties.lenderReference());
    payload.put("custodianOrganizationReference", parties.custodianReference());
    payload.put("amount", payment.getPaymentAmount().toPlainString());
    payload.put("currency", payment.getCurrency());
    payload.put("status", status);
    payload.put("aggregateVersion", aggregateVersion);
    payload.put("externalConfirmationReference", externalReference);
    payload.put("failureReason", failureReason);
    payload.put("updatedAt", clock.instant().toString());
    outgoing.enqueue(
        "REBATE_PAYMENT",
        EventEnvelope.builder()
            .eventId(eventId)
            .eventType(PAYMENT_EVENT)
            .schemaVersion(1)
            .producerService("CUSTODIAN")
            .destinationService("BORROWER")
            .correlationId(payment.getPaymentReference())
            .causationEventId(causationEventId)
            .aggregateReference(payment.getPaymentReference())
            .aggregateVersion(aggregateVersion)
            .occurredAt(clock.instant())
            .senderOrganizationReference(parties.custodianReference())
            .receiverOrganizationReference(parties.borrowerReference())
            .payload(payload)
            .build());
  }

  private Map<String, Object> response(LoanContract contract, RebatePaymentHistory payment) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("transactionReference", contract.getTransactionReference());
    result.put("paymentReference", payment.getPaymentReference());
    result.put("obligationReference", payment.getObligationReference());
    result.put("accrualDate", payment.getCalculationSnapshot().get("accrualDate"));
    result.put("calculationResultReference", payment.getCalculationResultReference());
    result.put("fromOrganizationReference",
        payment.getCalculationSnapshot().get("lenderOrganizationReference"));
    result.put("toOrganizationReference",
        payment.getCalculationSnapshot().get("borrowerOrganizationReference"));
    result.put("amount", payment.getPaymentAmount().toPlainString());
    result.put("currency", payment.getCurrency());
    result.put("status", payment.getStatus());
    result.put("externalConfirmationReference", payment.getExternalReference());
    result.put("failureReason", payment.getFailureReason());
    result.put("createdAt", payment.getCalculationSnapshot().get("createdAt"));
    result.put("updatedAt",
        payment.getPaidAt() == null
            ? payment.getCalculationSnapshot().getOrDefault(
                "failedAt", payment.getCalculationSnapshot().get("createdAt"))
            : payment.getPaidAt().toString());
    return result;
  }

  private RebatePaymentHistory lockedPayment(LoanContract contract, String reference) {
    return payments.findLockedByContractId(contract.getContractId()).stream()
        .filter(row -> reference.equals(row.getPaymentReference()))
        .findFirst()
        .orElseThrow(() -> SetupException.missing("Rebate payment instruction not found"));
  }

  private LoanContract lockedContract(String transactionReference) {
    return contracts
        .findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private void requirePayableContract(LoanContract contract) {
    if (!PAYABLE_CONTRACT_STATUSES.contains(contract.getContractStatus())) {
      throw SetupException.conflict("Rebates can be paid only for settled contracts");
    }
  }

  private void requireCustodian(Organization actor, FeeContractParties parties) {
    if (!Objects.equals(actor.getOrganizationId(), parties.custodianId())) {
      throw SetupException.missing("Loan contract not found in this Custodian scope");
    }
  }

  private BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw SetupException.conflict("Rebate accrual amount is invalid");
    }
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private String eventId(String paymentReference, long version) {
    return UUID.nameUUIDFromBytes(
            (PAYMENT_EVENT + ":" + paymentReference + ":" + version)
                .getBytes(StandardCharsets.UTF_8))
        .toString();
  }
}
