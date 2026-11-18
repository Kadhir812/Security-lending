package com.hexaware.borrower_service.service.fees;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.enums.BorrowerCashActivityType;
import com.hexaware.borrower_service.enums.BorrowerCashTransactionType;
import com.hexaware.borrower_service.service.cash.BorrowerCashService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RebateProjectionService {
  private final LoanContractViewRepository contractViews;
  private final LoanRequestRepository requests;
  private final BorrowerCashService cash;
  private final Clock clock;

  @Transactional
  public void applyAccrual(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String resultReference = text(payload.get("resultReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long aggregateVersion = integer(event.getAggregateVersion());
    if (!"REBATE_ACCRUAL_UPDATED".equals(event.getEventType())
        || !"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || integer(payload.get("payloadVersion")) != 1
        || !Objects.equals(event.getAggregateReference(), transactionReference)
        || !Objects.equals(event.getCorrelationId(), transactionReference)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || !Objects.equals(payload.get("status"), "POSTED")
        || transactionReference == null
        || resultReference == null
        || borrowerReference == null
        || custodianReference == null
        || aggregateVersion <= 0) {
      throw invalid("REBATE_ACCRUAL_ENVELOPE_MISMATCH");
    }

    LoanContractView view =
        contractViews
            .findByTransactionReference(transactionReference)
            .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request =
        requests
            .findByRequestReference(view.getRequestReference())
            .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())
        || !request.getSettlementCurrency().equals(payload.get("currency"))) {
      throw invalid("REBATE_ACCRUAL_PARTIES_OR_CURRENCY_MISMATCH");
    }

    BigDecimal amount = decimal(payload.get("amount"));
    BigDecimal grossAmount = decimal(payload.get("grossAmount"));
    BigDecimal cashBalance = decimal(payload.get("settledCashCollateralAmount"));
    BigDecimal annualRate = decimal(payload.get("annualRebatePercentage"));
    String accrualDate = text(payload.get("accrualDate"));
    String itemKey = text(payload.get("itemKey"));
    if (accrualDate == null
        || !Objects.equals(itemKey, "REBATE:" + accrualDate)
        || amount.scale() > 2
        || grossAmount.signum() < 0
        || grossAmount.scale() > 2
        || cashBalance.signum() <= 0
        || annualRate.signum() < 0
        || !"ACT_365".equals(payload.get("dayCountBasis"))
        || !"SETTLED_CASH_COLLATERAL".equals(payload.get("accrualBasis"))) {
      throw invalid("REBATE_ACCRUAL_CONTENT_INVALID");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> accruals = mapList(snapshot.get("rebateAccruals"));
    Map<String, Object> sameResult =
        accruals.stream()
            .filter(row -> resultReference.equals(row.get("resultReference")))
            .findFirst()
            .orElse(null);
    if (sameResult != null) {
      if (!decimal(sameResult.get("amount")).equals(amount)
          || !Objects.equals(sameResult.get("inputVersion"), payload.get("inputVersion"))) {
        throw invalid("REBATE_RESULT_REFERENCE_REUSED");
      }
      return;
    }
    long currentVersion = number(snapshot.get("rebateAccrualAggregateVersion"));
    if (aggregateVersion <= currentVersion) return;
    if (aggregateVersion != currentVersion + 1) {
      throw invalid("REBATE_ACCRUAL_VERSION_GAP");
    }
    String supersedes = text(payload.get("supersedesResultReference"));
    String latestItemResult =
        accruals.stream()
            .filter(row -> Objects.equals(row.get("itemKey"), itemKey))
            .map(row -> text(row.get("resultReference")))
            .filter(Objects::nonNull)
            .reduce((first, second) -> second)
            .orElse(null);
    if (!Objects.equals(supersedes, latestItemResult)) {
      throw invalid("REBATE_CORRECTION_BASE_MISSING");
    }
    Map<String, Object> accrual = new LinkedHashMap<>(payload);
    accrual.put("receivedAt", event.getOccurredAt().toString());
    accruals.add(accrual);
    snapshot.put("rebateAccruals", accruals);
    snapshot.put("rebateAccrualAggregateVersion", aggregateVersion);
    view.setContractSnapshot(snapshot);
    contractViews.save(view);
  }

  @Transactional
  public void applyPayment(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String paymentReference = text(payload.get("paymentReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    String resultReference = text(payload.get("calculationResultReference"));
    String itemKey = text(payload.get("itemKey"));
    String status = text(payload.get("status"));
    long aggregateVersion = integer(event.getAggregateVersion());
    if (!"REBATE_PAYMENT_UPDATED".equals(event.getEventType())
        || !"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || integer(payload.get("payloadVersion")) != 1
        || integer(payload.get("aggregateVersion")) != aggregateVersion
        || !Objects.equals(event.getAggregateReference(), paymentReference)
        || !Objects.equals(event.getCorrelationId(), paymentReference)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || event.getCausationEventId() == null
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || transactionReference == null
        || paymentReference == null
        || borrowerReference == null
        || custodianReference == null
        || resultReference == null
        || aggregateVersion < 1
        || status == null
        || !List.of("PENDING", "PAID", "FAILED").contains(status)) {
      throw invalid("REBATE_PAYMENT_ENVELOPE_MISMATCH");
    }

    LoanContractView view =
        contractViews
            .findByTransactionReference(transactionReference)
            .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request =
        requests
            .findByRequestReference(view.getRequestReference())
            .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())
        || !Objects.equals(request.getSettlementCurrency(), payload.get("currency"))) {
      throw invalid("REBATE_PAYMENT_PARTIES_OR_CURRENCY_MISMATCH");
    }
    BigDecimal amount = decimal(payload.get("amount"));
    String accrualDate = text(payload.get("accrualDate"));
    if (amount.signum() <= 0
        || amount.scale() > 2
        || accrualDate == null
        || !Objects.equals(itemKey, "REBATE:" + accrualDate)
        || !Objects.equals(payload.get("obligationReference"), itemKey)
        || ("PAID".equals(status)
            && text(payload.get("externalConfirmationReference")) == null)
        || ("FAILED".equals(status) && text(payload.get("failureReason")) == null)) {
      throw invalid("REBATE_PAYMENT_CONTENT_INVALID");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> payments = mapList(snapshot.get("rebatePayments"));
    int index = -1;
    for (int i = 0; i < payments.size(); i++) {
      if (paymentReference.equals(payments.get(i).get("paymentReference"))) {
        index = i;
        break;
      }
    }
    Map<String, Object> existing = index < 0 ? null : payments.get(index);
    long existingVersion = existing == null ? 0 : number(existing.get("aggregateVersion"));
    if (aggregateVersion <= existingVersion) return;
    if (existing == null && (aggregateVersion != 1 || !"PENDING".equals(status))) {
      throw invalid("REBATE_PAYMENT_CREATION_EVENT_MISSING");
    }
    if (existing != null
        && (existingVersion != 1
            || aggregateVersion != 2
            || !"PENDING".equals(existing.get("status"))
            || !List.of("PAID", "FAILED").contains(status)
            || !Objects.equals(existing.get("calculationResultReference"), resultReference)
            || !Objects.equals(existing.get("itemKey"), itemKey)
            || !Objects.equals(existing.get("currency"), payload.get("currency"))
            || decimal(existing.get("amount")).compareTo(amount) != 0)) {
      throw invalid("REBATE_PAYMENT_STATUS_TRANSITION_INVALID");
    }

    Map<String, Object> payment =
        existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
    payment.put("paymentReference", paymentReference);
    payment.put("obligationReference", itemKey);
    payment.put("itemKey", itemKey);
    payment.put("accrualDate", accrualDate);
    payment.put("calculationResultReference", resultReference);
    payment.put("amount", amount.toPlainString());
    payment.put("currency", payload.get("currency"));
    payment.put("status", status);
    payment.put("aggregateVersion", aggregateVersion);
    payment.put("externalConfirmationReference", payload.get("externalConfirmationReference"));
    payment.put("failureReason", payload.get("failureReason"));
    payment.put("updatedAt", event.getOccurredAt().toString());
    if (existing == null) payment.put("createdAt", event.getOccurredAt().toString());
    if (index < 0) payments.add(payment);
    else payments.set(index, payment);
    snapshot.put("rebatePayments", payments);
    snapshot.put("rebatePaymentAggregateVersion", aggregateVersion);
    view.setContractSnapshot(snapshot);
    contractViews.save(view);
    if ("PAID".equals(status)) {
      cash.recordTransaction(
          borrowerReference,
          request.getSettlementCurrency(),
          "CASH:REBATE:" + paymentReference,
          BorrowerCashTransactionType.CREDIT,
          BorrowerCashActivityType.REBATE_RECEIVED,
          amount,
          "PAYMENT",
          paymentReference,
          "Lending rebate received",
          event.getOccurredAt());
    }
  }

  private List<Map<String, Object>> mapList(Object value) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (value instanceof List<?> values) {
      for (Object item : values) {
        if (item instanceof Map<?, ?> source) {
          Map<String, Object> copy = new LinkedHashMap<>();
          source.forEach(
              (key, field) -> {
                if (key instanceof String name) copy.put(name, field);
              });
          result.add(copy);
        }
      }
    }
    return result;
  }

  private BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_REBATE_EVENT_AMOUNT");
    }
  }

  private long integer(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_REBATE_EVENT_VERSION");
    }
  }

  private long number(Object value) {
    return value == null ? 0 : integer(value);
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
