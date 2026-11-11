package com.hexaware.borrower_service.service.fees;

import com.hexaware.borrower_service.dto.request.FeePaymentRequest;
import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeePaymentService {
  private final LoanContractViewRepository contractViews;
  private final LoanRequestRepository requests;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  public List<Map<String, Object>> charges(
      Authentication authentication, String transactionReference) {
    LoanContractView view = scopedView(authentication, transactionReference);
    Map<String, Object> snapshot = view.getContractSnapshot();
    List<Map<String, Object>> chargeRows = mapList(snapshot.get("feeCharges"));
    List<Map<String, Object>> paymentRows = mapList(snapshot.get("feePayments"));
    Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
    for (Map<String, Object> charge : chargeRows) {
      String itemKey = text(charge.get("itemKey"));
      if (itemKey == null) continue;
      Map<String, Object> group = grouped.computeIfAbsent(itemKey, key -> {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("itemKey", key);
        value.put("accrualDate", charge.get("accrualDate"));
        value.put("currency", charge.get("currency"));
        value.put("amount", BigDecimal.ZERO);
        value.put("latestResultReference", charge.get("resultReference"));
        return value;
      });
      group.put("amount", decimal(group.get("amount")).add(decimal(charge.get("amount"))));
      group.put("latestResultReference", charge.get("resultReference"));
    }
    for (Map<String, Object> group : grouped.values()) {
      BigDecimal paid = BigDecimal.ZERO;
      BigDecimal pending = BigDecimal.ZERO;
      for (Map<String, Object> payment : paymentRows) {
        String status = text(payment.get("status"));
        for (Map<String, Object> allocation : mapList(payment.get("allocations"))) {
          if (!Objects.equals(group.get("itemKey"), allocation.get("itemKey"))) continue;
          if ("PAID".equals(status)) paid = paid.add(decimal(allocation.get("amount")));
          if (List.of("SUBMITTED", "PENDING").contains(status)) {
            pending = pending.add(decimal(allocation.get("amount")));
          }
        }
      }
      BigDecimal total = decimal(group.get("amount"));
      group.put("paidAmount", paid);
      group.put("pendingAmount", pending);
      group.put("outstandingAmount", total.subtract(paid).max(BigDecimal.ZERO));
      group.put("availableAmount",
          total.subtract(paid).subtract(pending).max(BigDecimal.ZERO));
      group.put("creditAmount", paid.subtract(total).max(BigDecimal.ZERO));
    }
    return new ArrayList<>(grouped.values());
  }

  public List<Map<String, Object>> list(
      Authentication authentication, String transactionReference) {
    LoanContractView view = scopedView(authentication, transactionReference);
    return mapList(view.getContractSnapshot().get("feePayments"));
  }

  @Transactional
  public Map<String, Object> submit(
      Authentication authentication,
      String transactionReference,
      String paymentReference,
      FeePaymentRequest request) {
    LocalUserAuthentication user = trader(authentication);
    if (paymentReference == null
        || !paymentReference.matches("[A-Za-z0-9._:-]{1,80}")) {
      throw BorrowerException.invalid("A valid Idempotency-Key is required");
    }
    LoanContractView view = contractViews.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    var loanRequest = requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    if (!"ON_LOAN".equals(view.getContractSnapshot().get("contractStatus"))) {
      throw BorrowerException.conflict("Fee payments require an on-loan contract");
    }
    if (!Objects.equals(loanRequest.getSettlementCurrency(), request.getCurrency())) {
      throw BorrowerException.invalid("Payment currency must match the contract");
    }
    BigDecimal allocated = request.getAllocations().stream()
        .map(allocation -> allocation.getAmount())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (allocated.compareTo(request.getAmount()) != 0) {
      throw BorrowerException.invalid("Payment allocation total must equal payment amount");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> payments =
        mapList(snapshot.computeIfAbsent("feePayments", key -> new ArrayList<>()));
    List<Map<String, Object>> charges = mapList(snapshot.get("feeCharges"));
    Map<String, Object> existing = payments.stream()
        .filter(row -> paymentReference.equals(row.get("paymentReference")))
        .findFirst()
        .orElse(null);
    List<Map<String, Object>> allocations = new ArrayList<>();
    for (var allocation : request.getAllocations()) {
      Map<String, Object> charge = charges.stream()
          .filter(row -> allocation.getCalculationResultReference()
              .equals(row.get("resultReference")))
          .findFirst()
          .orElseThrow(() -> BorrowerException.conflict("Payment allocation references an unknown charge"));
      if (!Objects.equals(charge.get("itemKey"), allocation.getItemKey())
          || !Objects.equals(charge.get("currency"), request.getCurrency())) {
        throw BorrowerException.conflict("Payment allocation does not match the charge");
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("calculationResultReference", allocation.getCalculationResultReference());
      row.put("itemKey", allocation.getItemKey());
      row.put("amount", allocation.getAmount().toPlainString());
      allocations.add(row);
    }
    if (existing != null) {
      if (decimal(existing.get("amount")).compareTo(request.getAmount()) != 0
          || !Objects.equals(existing.get("currency"), request.getCurrency())
          || !canonicalAllocations(mapList(existing.get("allocations")))
              .equals(canonicalAllocations(allocations))) {
        throw BorrowerException.conflict("Payment idempotency key was reused with different inputs");
      }
      return existing;
    }
    Map<String, Map<String, Object>> dueByItem = new LinkedHashMap<>();
    for (Map<String, Object> charge : charges) {
      String itemKey = text(charge.get("itemKey"));
      if (itemKey == null) continue;
      Map<String, Object> item = dueByItem.computeIfAbsent(itemKey, key -> {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("outstandingAmount", BigDecimal.ZERO);
        value.put("latestResultReference", null);
        return value;
      });
      item.put("outstandingAmount",
          decimal(item.get("outstandingAmount")).add(decimal(charge.get("amount"))));
      item.put("latestResultReference", charge.get("resultReference"));
    }
    for (Map<String, Object> payment : payments) {
      String status = text(payment.get("status"));
      if (!List.of("SUBMITTED", "PENDING", "PAID").contains(status)) continue;
      for (Map<String, Object> allocation : mapList(payment.get("allocations"))) {
        Map<String, Object> item = dueByItem.get(text(allocation.get("itemKey")));
        if (item != null) {
          item.put("outstandingAmount",
              decimal(item.get("outstandingAmount")).subtract(decimal(allocation.get("amount"))));
        }
      }
    }
    Map<String, BigDecimal> requestedByItem = new HashMap<>();
    for (Map<String, Object> allocation : allocations) {
      requestedByItem.merge(
          text(allocation.get("itemKey")), decimal(allocation.get("amount")), BigDecimal::add);
    }
    for (Map.Entry<String, BigDecimal> entry : requestedByItem.entrySet()) {
      Map<String, Object> item = dueByItem.get(entry.getKey());
      if (item == null
          || !Objects.equals(item.get("latestResultReference"),
              allocations.stream()
                  .filter(allocation -> entry.getKey().equals(allocation.get("itemKey")))
                  .map(allocation -> allocation.get("calculationResultReference"))
                  .findFirst().orElse(null))
          || entry.getValue().compareTo(
              decimal(item.get("outstandingAmount")).max(BigDecimal.ZERO)) > 0) {
        throw BorrowerException.conflict("Payment exceeds the current charge balance");
      }
    }

    Map<String, Object> payment = new LinkedHashMap<>();
    payment.put("paymentReference", paymentReference);
    payment.put("amount", request.getAmount().toPlainString());
    payment.put("currency", request.getCurrency());
    payment.put("allocations", allocations);
    payment.put("status", "SUBMITTED");
    payment.put("aggregateVersion", 0);
    payment.put("submittedAt", clock.instant().toString());
    payments.add(payment);
    snapshot.put("feePayments", payments);
    view.setContractSnapshot(snapshot);
    contractViews.save(view);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", transactionReference);
    payload.put("paymentReference", paymentReference);
    payload.put("borrowerOrganizationReference", user.getOrganizationReference());
    payload.put("custodianOrganizationReference", loanRequest.getCustodianOrganizationReference());
    payload.put("amount", request.getAmount().toPlainString());
    payload.put("currency", request.getCurrency());
    payload.put("allocations", allocations);
    outgoing.enqueue("FEE_PAYMENT", EventEnvelope.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("FEE_PAYMENT_SUBMITTED")
        .schemaVersion(1)
        .producerService("BORROWER")
        .destinationService("CUSTODIAN")
        .correlationId(paymentReference)
        .aggregateReference(paymentReference)
        .aggregateVersion(0L)
        .occurredAt(clock.instant())
        .senderOrganizationReference(user.getOrganizationReference())
        .receiverOrganizationReference(loanRequest.getCustodianOrganizationReference())
        .payload(payload)
        .build());
    return payment;
  }

  private LoanContractView scopedView(
      Authentication authentication, String transactionReference) {
    LocalUserAuthentication user = trader(authentication);
    LoanContractView view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    return view;
  }

  private LocalUserAuthentication trader(Authentication authentication) {
    if (!(authentication instanceof LocalUserAuthentication user)
        || !user.isAuthenticated()
        || user.getAuthorities().stream()
            .noneMatch(authority -> authority.getAuthority().equals("ROLE_TRADER"))) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Authenticated borrower required");
    }
    return user;
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
      throw BorrowerException.conflict("Projected fee amount is invalid");
    }
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private List<String> canonicalAllocations(List<Map<String, Object>> allocations) {
    return allocations.stream()
        .map(allocation -> text(allocation.get("itemKey")) + ":"
            + text(allocation.get("calculationResultReference")) + ":"
            + decimal(allocation.get("amount")).setScale(2).toPlainString())
        .sorted()
        .toList();
  }
}
