package com.hexaware.borrower_service.service.fees;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
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
public class FeeProjectionService {
  private final LoanContractViewRepository contractViews;
  private final LoanRequestRepository requests;
  private final Clock clock;

  @Transactional
  public void applyCharge(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String resultReference = text(payload.get("resultReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long aggregateVersion = integer(event.getAggregateVersion());
    if (!"LENDING_FEE_CHARGE_UPDATED".equals(event.getEventType())
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
      throw invalid("LENDING_FEE_CHARGE_ENVELOPE_MISMATCH");
    }

    LoanContractView view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request = requests.findByRequestReference(view.getRequestReference())
        .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())
        || !request.getSettlementCurrency().equals(payload.get("currency"))) {
      throw invalid("LENDING_FEE_CHARGE_PARTIES_OR_CURRENCY_MISMATCH");
    }
    if (!"LENDING_FEE".equals(payload.get("feeBasis"))
        && !"DAILY_MARKET_VALUE".equals(payload.get("feeBasis"))) {
      throw invalid("UNSUPPORTED_LENDING_FEE_BASIS");
    }
    BigDecimal amount = decimal(payload.get("amount"));
    decimal(payload.get("grossAmount"));
    String accrualDate = text(payload.get("accrualDate"));
    String itemKey = text(payload.get("itemKey"));
    if (accrualDate == null
        || !Objects.equals(itemKey, "LENDING_FEE:" + accrualDate)
        || amount.scale() > 2
        || !Objects.equals(payload.get("dayCountBasis"), "ACT_365")) {
      throw invalid("LENDING_FEE_CHARGE_CONTENT_INVALID");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> charges = mapList(snapshot.get("feeCharges"));
    Map<String, Object> sameResult = charges.stream()
        .filter(row -> resultReference.equals(row.get("resultReference")))
        .findFirst().orElse(null);
    if (sameResult != null) {
      if (!decimal(sameResult.get("amount")).equals(decimal(payload.get("amount")))
          || !Objects.equals(sameResult.get("inputVersion"), payload.get("inputVersion"))) {
        throw invalid("LENDING_FEE_RESULT_REFERENCE_REUSED");
      }
      return;
    }
    long currentVersion = number(snapshot.get("feeChargeAggregateVersion"));
    if (aggregateVersion <= currentVersion) return;
    if (aggregateVersion != currentVersion + 1) {
      throw invalid("LENDING_FEE_CHARGE_VERSION_GAP");
    }
    String supersedes = text(payload.get("supersedesResultReference"));
    String latestItemResult = charges.stream()
        .filter(row -> Objects.equals(row.get("itemKey"), itemKey))
        .map(row -> text(row.get("resultReference")))
        .filter(Objects::nonNull)
        .reduce((first, second) -> second)
        .orElse(null);
    if (!Objects.equals(supersedes, latestItemResult)) {
      throw invalid("LENDING_FEE_CORRECTION_BASE_MISSING");
    }
    Map<String, Object> charge = new LinkedHashMap<>(payload);
    charge.put("receivedAt", event.getOccurredAt().toString());
    charges.add(charge);
    snapshot.put("feeCharges", charges);
    snapshot.put("feeChargeAggregateVersion", aggregateVersion);
    view.setContractSnapshot(snapshot);
    contractViews.save(view);
  }

  @Transactional
  public void applyPayment(EventEnvelope event, String expectedEventType) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = text(payload.get("transactionReference"));
    String paymentReference = text(payload.get("paymentReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    long aggregateVersion = integer(event.getAggregateVersion());
    String status = text(payload.get("status"));
    if (!expectedEventType.equals(event.getEventType())
        || !"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || integer(payload.get("payloadVersion")) != 1
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
        || aggregateVersion < 1
        || !List.of("PENDING", "PAID", "FAILED").contains(status)) {
      throw invalid("FEE_PAYMENT_EVENT_ENVELOPE_MISMATCH");
    }
    LoanContractView view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request = requests.findByRequestReference(view.getRequestReference())
        .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())
        || !Objects.equals(request.getSettlementCurrency(), payload.get("currency"))) {
      throw invalid("FEE_PAYMENT_EVENT_PARTIES_OR_CURRENCY_MISMATCH");
    }
    decimal(payload.get("amount"));
    List<Map<String, Object>> allocations = mapList(payload.get("allocations"));
    if (allocations.isEmpty()) throw invalid("FEE_PAYMENT_EVENT_HAS_NO_ALLOCATIONS");
    BigDecimal allocated = BigDecimal.ZERO;
    for (Map<String, Object> allocation : allocations) {
      BigDecimal allocationAmount = decimal(allocation.get("amount"));
      if (allocationAmount.signum() <= 0
          || allocationAmount.scale() > 2
          || text(allocation.get("itemKey")) == null
          || text(allocation.get("calculationResultReference")) == null) {
        throw invalid("FEE_PAYMENT_EVENT_ALLOCATION_INVALID");
      }
      allocated = allocated.add(allocationAmount);
    }
    if (allocated.compareTo(decimal(payload.get("amount"))) != 0) {
      throw invalid("FEE_PAYMENT_EVENT_ALLOCATION_TOTAL_MISMATCH");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> payments = mapList(snapshot.get("feePayments"));
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
      throw invalid("FEE_PAYMENT_SUBMISSION_EVENT_MISSING");
    }
    if (existing != null) {
      boolean received =
          existingVersion == 0
              && aggregateVersion == 1
              && "SUBMITTED".equals(existing.get("status"))
              && "PENDING".equals(status);
      boolean finalized =
          existingVersion == 1
              && aggregateVersion == 2
              && List.of("SUBMITTED", "PENDING").contains(existing.get("status"))
              && List.of("PAID", "FAILED").contains(status);
      if ((!received && !finalized)
          || decimal(existing.get("amount")).compareTo(decimal(payload.get("amount"))) != 0
          || !Objects.equals(existing.get("currency"), payload.get("currency"))
          || !Objects.equals(existing.get("allocations"), allocations)) {
        throw invalid("FEE_PAYMENT_STATUS_TRANSITION_INVALID");
      }
    }

    Map<String, Object> payment = existing == null
        ? new LinkedHashMap<>()
        : new LinkedHashMap<>(existing);
    payment.put("paymentReference", paymentReference);
    payment.put("amount", decimal(payload.get("amount")).toPlainString());
    payment.put("currency", payload.get("currency"));
    payment.put("allocations", allocations);
    payment.put("status", status);
    payment.put("aggregateVersion", aggregateVersion);
    payment.put("updatedAt", event.getOccurredAt().toString());
    payment.put("externalConfirmationReference", payload.get("externalConfirmationReference"));
    payment.put("failureReason", payload.get("failureReason"));
    if (existing == null) payment.put("submittedAt", event.getOccurredAt().toString());
    if (index < 0) payments.add(payment);
    else payments.set(index, payment);
    snapshot.put("feePayments", payments);
    view.setContractSnapshot(snapshot);
    contractViews.save(view);
  }

  private List<Map<String, Object>> mapList(Object value) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (value instanceof List<?> values) {
      for (Object item : values) {
        if (item instanceof Map<?, ?> source) {
          Map<String, Object> copy = new LinkedHashMap<>();
          source.forEach((key, field) -> {
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
      throw invalid("INVALID_FEE_EVENT_AMOUNT");
    }
  }

  private long integer(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_FEE_EVENT_VERSION");
    }
  }

  private long number(Object value) {
    if (value == null) return 0;
    return integer(value);
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
