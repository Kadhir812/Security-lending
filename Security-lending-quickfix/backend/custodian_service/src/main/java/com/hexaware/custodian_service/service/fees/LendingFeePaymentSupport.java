package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.entity.LendingFeePayment;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LendingFeePaymentRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LendingFeePaymentSupport {
  private final LendingFeePaymentRepository payments;
  private final LoanContractRepository contracts;
  private final OutboxEventRepository outbox;
  private final LendingFeeAccrualService accruals;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  void validateAvailableAllocations(
      LoanContract contract,
      String currency,
      List<Map<String, Object>> requested,
      List<LendingFeePayment> existingPayments) {
    Map<String, List<Map<String, Object>>> chargeGroups = new LinkedHashMap<>();
    for (Map<String, Object> charge : accruals.chargeEvents(
        contract.getTransactionReference())) {
      if (!currency.equals(charge.get("currency"))) {
        throw SetupException.conflict("A fee charge has a different contract currency");
      }
      String itemKey = text(charge.get("itemKey"));
      chargeGroups.computeIfAbsent(itemKey, ignored -> new ArrayList<>()).add(charge);
    }
    Map<String, BigDecimal> requestedByItem = new LinkedHashMap<>();
    for (Map<String, Object> allocation : requested) {
      String itemKey = text(allocation.get("itemKey"));
      String resultReference = text(allocation.get("calculationResultReference"));
      List<Map<String, Object>> charges = chargeGroups.get(itemKey);
      if (charges == null || charges.isEmpty()
          || !Objects.equals(
              charges.getLast().get("resultReference"), resultReference)) {
        throw new InvalidEnvelopeException("FEE_PAYMENT_CHARGE_REFERENCE_MISMATCH");
      }
      requestedByItem.merge(
          itemKey, positiveAmount(allocation.get("amount")), BigDecimal::add);
    }
    for (Map.Entry<String, BigDecimal> entry : requestedByItem.entrySet()) {
      List<Map<String, Object>> charges = chargeGroups.get(entry.getKey());
      BigDecimal balance = charges.stream()
          .map(charge -> decimal(charge.get("amount")))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal reservedOrPaid = existingPayments.stream()
          .filter(row -> Objects.equals(
              row.getCalculationSnapshot().get("chargeItemKey"), entry.getKey()))
          .filter(row -> "PENDING".equals(row.getStatus()) || "PAID".equals(row.getStatus()))
          .map(LendingFeePayment::getPaymentAmount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (entry.getValue().compareTo(balance.subtract(reservedOrPaid).max(BigDecimal.ZERO)) > 0) {
        throw new InvalidEnvelopeException("FEE_PAYMENT_EXCEEDS_OUTSTANDING_CHARGE");
      }
    }
  }

  void enqueueUpdate(
      EventEnvelope source,
      LoanContract contract,
      FeeContractParties parties,
      String paymentReference,
      String eventType,
      String status,
      BigDecimal amount,
      String currency,
      List<Map<String, Object>> allocations,
      long aggregateVersion,
      String externalReference,
      String failureReason) {
    String eventId = UUID.nameUUIDFromBytes(
        (eventType + ":" + paymentReference + ":" + aggregateVersion + ":"
            + Objects.toString(externalReference, ""))
            .getBytes(StandardCharsets.UTF_8))
        .toString();
    if (outbox.existsByEventId(eventId)) return;
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", contract.getTransactionReference());
    payload.put("paymentReference", paymentReference);
    payload.put("borrowerOrganizationReference", parties.borrowerReference());
    payload.put("custodianOrganizationReference", parties.custodianReference());
    payload.put("lenderOrganizationReference", parties.lenderReference());
    payload.put("amount", amount.toPlainString());
    payload.put("currency", currency);
    payload.put("status", status);
    payload.put("allocations", allocations);
    payload.put("aggregateVersion", aggregateVersion);
    payload.put("simulated", true);
    payload.put("externalConfirmationReference", externalReference);
    payload.put("failureReason", failureReason);
    payload.put("updatedAt", clock.instant().toString());
    String causationEventId = source == null
        ? findPaymentGroup(contract, paymentReference).getFirst()
            .getCalculationSnapshot().get("sourceEventId").toString()
        : source.getEventId();
    outgoing.enqueue("FEE_PAYMENT", EventEnvelope.builder()
        .eventId(eventId)
        .eventType(eventType)
        .schemaVersion(1)
        .producerService("CUSTODIAN")
        .destinationService("BORROWER")
        .correlationId(paymentReference)
        .causationEventId(causationEventId)
        .aggregateReference(paymentReference)
        .aggregateVersion(aggregateVersion)
        .occurredAt(clock.instant())
        .senderOrganizationReference(parties.custodianReference())
        .receiverOrganizationReference(parties.borrowerReference())
        .payload(payload)
        .build());
  }

  List<Map<String, Object>> paymentAllocations(List<LendingFeePayment> rows) {
    return rows.stream()
        .sorted(Comparator.comparing(LendingFeePayment::getPaymentId))
        .map(row -> {
          Map<String, Object> allocation = new LinkedHashMap<>();
          allocation.put("calculationResultReference", row.getCalculationResultReference());
          allocation.put("itemKey", row.getCalculationSnapshot().get("chargeItemKey"));
          allocation.put("amount", row.getPaymentAmount().toPlainString());
          return allocation;
        })
        .toList();
  }

  Map<String, Object> paymentResponse(
      String paymentReference, List<LendingFeePayment> rows, boolean simulated) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("paymentReference", paymentReference);
    response.put("transactionReference", contracts.findById(rows.getFirst().getContractId())
        .orElseThrow(() -> SetupException.missing("Loan contract not found"))
        .getTransactionReference());
    response.put("amount", rows.stream()
        .map(LendingFeePayment::getPaymentAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString());
    response.put("currency", rows.getFirst().getCurrency());
    response.put("status", rows.getFirst().getStatus());
    response.put("allocations", paymentAllocations(rows));
    response.put("externalConfirmationReference", rows.stream()
        .map(LendingFeePayment::getExternalReference)
        .filter(Objects::nonNull).findFirst().orElse(null));
    response.put("simulated", simulated
        || Boolean.TRUE.equals(rows.getFirst().getCalculationSnapshot().get("simulatedPaymentAdapter")));
    response.put("updatedAt", rows.stream()
        .map(row -> row.getPaidAt() == null
            ? text(row.getCalculationSnapshot().get("failedAt"))
            : row.getPaidAt().toString())
        .filter(Objects::nonNull).max(String::compareTo).orElse(null));
    return response;
  }

  List<LendingFeePayment> paymentGroup(LoanContract contract, String reference) {
    List<LendingFeePayment> rows = payments.findLockedByContractId(contract.getContractId()).stream()
        .filter(row -> reference.equals(
            row.getCalculationSnapshot().get("paymentRequestReference")))
        .sorted(Comparator.comparing(LendingFeePayment::getPaymentId))
        .toList();
    if (rows.isEmpty()) throw SetupException.missing("Fee payment instruction not found");
    return rows;
  }

  List<LendingFeePayment> findPaymentGroup(LoanContract contract, String reference) {
    return payments.findByContractIdOrderByPaymentId(contract.getContractId()).stream()
        .filter(row -> reference.equals(
            row.getCalculationSnapshot().get("paymentRequestReference")))
        .sorted(Comparator.comparing(LendingFeePayment::getPaymentId))
        .toList();
  }

  String allocationPaymentReference(String paymentReference, Object resultReference, int index) {
    return UUID.nameUUIDFromBytes((paymentReference + ":" + resultReference + ":" + index)
        .getBytes(StandardCharsets.UTF_8)).toString();
  }

  List<Map<String, Object>> allocationList(Object value) {
    if (!(value instanceof List<?> source) || source.isEmpty() || source.size() > 100) {
      throw new InvalidEnvelopeException("INVALID_FEE_PAYMENT_ALLOCATIONS");
    }
    List<Map<String, Object>> allocations = new ArrayList<>();
    for (Object item : source) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new InvalidEnvelopeException("INVALID_FEE_PAYMENT_ALLOCATION");
      }
      String resultReference = text(map.get("calculationResultReference"));
      String itemKey = text(map.get("itemKey"));
      BigDecimal amount = positiveAmount(map.get("amount"));
      if (resultReference == null || resultReference.length() != 36
          || itemKey == null || !itemKey.matches("LENDING_FEE:[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        throw new InvalidEnvelopeException("INVALID_FEE_PAYMENT_ALLOCATION");
      }
      Map<String, Object> allocation = new LinkedHashMap<>();
      allocation.put("calculationResultReference", resultReference);
      allocation.put("itemKey", itemKey);
      allocation.put("amount", amount.toPlainString());
      allocations.add(allocation);
    }
    return allocations;
  }

  String requestHash(Map<String, Object> payload) {
    List<Map<String, Object>> allocations = allocationList(payload.get("allocations"));
    allocations.sort(Comparator
        .comparing((Map<String, Object> row) -> text(row.get("itemKey")))
        .thenComparing(row -> text(row.get("calculationResultReference")))
        .thenComparing(row -> text(row.get("amount"))));
    String canonical = text(payload.get("transactionReference")) + "|"
        + text(payload.get("paymentReference")) + "|"
        + text(payload.get("currency")) + "|"
        + decimal(payload.get("amount")).setScale(2).toPlainString() + "|"
        + allocations.stream()
            .map(row -> row.get("itemKey") + ":" + row.get("calculationResultReference")
                + ":" + decimal(row.get("amount")).setScale(2).toPlainString())
            .collect(Collectors.joining("|"));
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime", exception);
    }
  }

  BigDecimal positiveAmount(Object value) {
    BigDecimal amount = decimal(value);
    if (amount.signum() <= 0 || amount.scale() > 2) {
      throw new InvalidEnvelopeException("INVALID_FEE_PAYMENT_AMOUNT");
    }
    return amount;
  }

  BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw new InvalidEnvelopeException("INVALID_FEE_PAYMENT_AMOUNT");
    }
  }

  String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  long number(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw new InvalidEnvelopeException("INVALID_FEE_PAYMENT_VERSION");
    }
  }
}
