package com.hexaware.borrower_service.service.collateral;

import com.hexaware.borrower_service.dto.request.CashCollateralInstructionRequest;
import com.hexaware.borrower_service.dto.request.FinalCollateralReleaseRequest;
import com.hexaware.borrower_service.dto.request.SecuritiesReturnRequest;
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
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollateralInstructionService {
  private final LoanContractViewRepository contractViews;
  private final LoanRequestRepository requests;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Map<String, Object> progress(
      Authentication authentication, String transactionReference) {
    LocalUserAuthentication user = trader(authentication);
    var view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    Map<String, Object> snapshot = view.getContractSnapshot();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("transactionReference", transactionReference);
    response.put("contractStatus", snapshot.get("contractStatus"));
    response.put("quantity", snapshot.get("quantity"));
    response.put("deliveredQuantity", snapshot.getOrDefault("deliveredQuantity", 0));
    response.put("returnedQuantity", snapshot.getOrDefault("returnedQuantity", 0));
    response.put("outstandingQuantity", snapshot.getOrDefault("outstandingQuantity", 0));
    response.put("requiredCashCollateral", snapshot.get("requiredCashCollateral"));
    response.put("confirmedCashCollateral", snapshot.getOrDefault("confirmedCashCollateral", "0"));
    response.put("collateralInstructions", instructionList(snapshot.get("collateralInstructions")));
    response.put("collateralReleaseRequests",
        instructionList(snapshot.get("collateralReleaseRequests")));
    response.put("collateralReleaseInstructions",
        instructionList(snapshot.get("collateralReleaseInstructions")));
    response.put("settlementInstructions", instructionList(snapshot.get("settlementInstructions")));
    return response;
  }

  @Transactional
  public Map<String, Object> submit(
      Authentication authentication,
      String requestReference,
      CashCollateralInstructionRequest request) {
    LocalUserAuthentication user = trader(authentication);
    var loanRequest = requests.findByRequestReferenceAndBorrowerOrganizationReference(
        requestReference, user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Loan request not found"));
    var view = contractViews.findByRequestReference(requestReference)
        .orElseThrow(() -> BorrowerException.missing("Approved contract not found"));
    validateContract(view, request);

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> instructions =
        instructionList(snapshot.computeIfAbsent("collateralInstructions", key -> new ArrayList<>()));
    for (Map<String, Object> existing : instructions) {
      if (request.instructionReference().equals(existing.get("instructionReference"))) {
        if (new BigDecimal(existing.get("amount").toString()).compareTo(request.amount()) != 0
            || !request.currency().equals(existing.get("currency"))) {
          throw BorrowerException.conflict("Collateral instruction reference was reused");
        }
        return existing;
      }
    }

    Map<String, Object> instruction = new LinkedHashMap<>();
    instruction.put("instructionReference", request.instructionReference());
    instruction.put("amount", request.amount().toPlainString());
    instruction.put("currency", request.currency());
    instruction.put("collateralType", "CASH");
    instruction.put("status", "SUBMITTED");
    instruction.put("submittedAt", clock.instant().toString());
    instructions.add(instruction);
    snapshot.put("collateralInstructions", instructions);
    view.setContractSnapshot(snapshot);
    contractViews.save(view);

    String custodianReference = loanRequest.getCustodianOrganizationReference();
    Map<String, Object> payload = new HashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", view.getTransactionReference());
    payload.put("instructionReference", request.instructionReference());
    payload.put("borrowerOrganizationReference", user.getOrganizationReference());
    payload.put("custodianOrganizationReference", custodianReference);
    payload.put("amount", request.amount().toPlainString());
    payload.put("currency", request.currency());
    payload.put("collateralType", "CASH");
    payload.put("marginCallReference", null);

    outgoing.enqueue("COLLATERAL_INSTRUCTION", EventEnvelope.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("COLLATERAL_INSTRUCTION_SUBMITTED")
        .schemaVersion(1)
        .producerService("BORROWER")
        .destinationService("CUSTODIAN")
        .correlationId(request.instructionReference())
        .aggregateReference(request.instructionReference())
        .aggregateVersion(0L)
        .occurredAt(clock.instant())
        .senderOrganizationReference(user.getOrganizationReference())
        .receiverOrganizationReference(custodianReference)
        .payload(payload)
        .build());
    return instruction;
  }

  @Transactional
  public Map<String, Object> submitTopUp(
      Authentication authentication,
      String transactionReference,
      String marginCallReference,
      CashCollateralInstructionRequest request) {
    LocalUserAuthentication user = trader(authentication);
    var view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    var loanRequest = requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    validateCashRequest(request, view.getContractSnapshot());
    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    Map<String, Object> call = findMarginCall(snapshot, marginCallReference);
    String callStatus = String.valueOf(call.get("status"));

    List<Map<String, Object>> instructions =
        instructionList(snapshot.computeIfAbsent("collateralInstructions", key -> new ArrayList<>()));
    for (Map<String, Object> existing : instructions) {
      if (request.instructionReference().equals(existing.get("instructionReference"))) {
        if (!marginCallReference.equals(existing.get("marginCallReference"))
            || new BigDecimal(existing.get("amount").toString()).compareTo(request.amount()) != 0
            || !request.currency().equals(existing.get("currency"))) {
          throw BorrowerException.conflict("Top-up instruction reference was reused");
        }
        return existing;
      }
    }
    if (!"OPEN".equals(callStatus)
        && !"PARTIALLY_SATISFIED".equals(callStatus)
        && !"BREACHED".equals(callStatus)) {
      throw BorrowerException.conflict("Only an active margin call accepts a CASH top-up");
    }

    Map<String, Object> instruction = new LinkedHashMap<>();
    instruction.put("instructionReference", request.instructionReference());
    instruction.put("marginCallReference", marginCallReference);
    instruction.put("amount", request.amount().toPlainString());
    instruction.put("currency", request.currency());
    instruction.put("collateralType", "CASH");
    instruction.put("status", "SUBMITTED");
    instruction.put("submittedAt", clock.instant().toString());
    instructions.add(instruction);
    snapshot.put("collateralInstructions", instructions);
    snapshot.put("contractStatus", "ON_LOAN");
    view.setContractSnapshot(snapshot);
    contractViews.save(view);

    String custodianReference = loanRequest.getCustodianOrganizationReference();
    Map<String, Object> payload = new HashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", view.getTransactionReference());
    payload.put("instructionReference", request.instructionReference());
    payload.put("borrowerOrganizationReference", user.getOrganizationReference());
    payload.put("custodianOrganizationReference", custodianReference);
    payload.put("amount", request.amount().toPlainString());
    payload.put("currency", request.currency());
    payload.put("collateralType", "CASH");
    payload.put("marginCallReference", marginCallReference);

    outgoing.enqueue("COLLATERAL_INSTRUCTION", EventEnvelope.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("COLLATERAL_INSTRUCTION_SUBMITTED")
        .schemaVersion(1)
        .producerService("BORROWER")
        .destinationService("CUSTODIAN")
        .correlationId(request.instructionReference())
        .aggregateReference(request.instructionReference())
        .aggregateVersion(0L)
        .occurredAt(clock.instant())
        .senderOrganizationReference(user.getOrganizationReference())
        .receiverOrganizationReference(custodianReference)
        .payload(payload)
        .build());
    return instruction;
  }

  @Transactional
  public Map<String, Object> submitReturn(
      Authentication authentication,
      String transactionReference,
      SecuritiesReturnRequest request) {
    LocalUserAuthentication user = trader(authentication);
    LoanContractView view = contractViews.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    var loanRequest = requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    String status = String.valueOf(snapshot.get("contractStatus"));
    if (!"ON_LOAN".equals(status) && !"RECALLED".equals(status)) {
      throw BorrowerException.conflict("Only a settled loan can be returned");
    }
    if (request == null || request.quantity() == null || request.quantity() <= 0
        || request.instructionReference() == null
        || !request.instructionReference().matches("[A-Za-z0-9._:-]{1,80}")) {
      throw BorrowerException.invalid("A return reference and positive whole quantity are required");
    }
    if (!loanRequest.getSecurityIsin().equals(snapshot.get("securityIsin"))) {
      throw BorrowerException.conflict("Borrower contract security does not match the approved request");
    }

    long outstanding = number(snapshot.get("outstandingQuantity"));
    if (outstanding <= 0) throw BorrowerException.conflict("Contract has no outstanding quantity");
    List<Map<String, Object>> returns =
        instructionList(snapshot.computeIfAbsent("returnInstructions", key -> new ArrayList<>()));
    for (Map<String, Object> existing : returns) {
      if (!request.instructionReference().equals(existing.get("instructionReference"))) continue;
      if (number(existing.get("quantity")) != request.quantity()
          || !Objects.equals(request.recallReference(), existing.get("recallReference"))) {
        throw BorrowerException.conflict("Return instruction reference was reused");
      }
      return existing;
    }

    long pendingQuantity = returns.stream()
        .filter(row -> List.of("SUBMITTED", "PENDING", "PROCESSING")
            .contains(String.valueOf(row.get("status"))))
        .mapToLong(row -> number(row.get("quantity")))
        .sum();
    if (request.quantity() > outstanding - pendingQuantity) {
      throw BorrowerException.conflict("Return quantity exceeds outstanding quantity after pending returns");
    }
    Map<String, Object> recall = request.recallReference() == null
        ? null : findRecall(snapshot, request.recallReference());
    if (recall != null) {
      String recallStatus = String.valueOf(recall.get("status"));
      if (!List.of("OPEN", "PROCESSING", "PARTIALLY_RETURNED", "OVERDUE", "DEFAULTED")
          .contains(recallStatus)) {
        throw BorrowerException.conflict("Recall is not accepting returns");
      }
      long pendingRecallQuantity = returns.stream()
          .filter(row -> request.recallReference().equals(row.get("recallReference")))
          .filter(row -> List.of("SUBMITTED", "PENDING", "PROCESSING")
              .contains(String.valueOf(row.get("status"))))
          .mapToLong(row -> number(row.get("quantity")))
          .sum();
      long recallRemaining = number(recall.get("requestedQuantity"))
          - number(recall.get("returnedQuantity")) - pendingRecallQuantity;
      if (request.quantity() > recallRemaining) {
        throw BorrowerException.conflict("Return quantity exceeds the recall's remaining quantity");
      }
    }

    Map<String, Object> instruction = new LinkedHashMap<>();
    instruction.put("instructionReference", request.instructionReference());
    instruction.put("recallReference", request.recallReference());
    instruction.put("quantity", request.quantity());
    instruction.put("securityIsin", loanRequest.getSecurityIsin());
    instruction.put("currency", loanRequest.getSettlementCurrency());
    instruction.put("status", "SUBMITTED");
    instruction.put("submittedAt", clock.instant().toString());
    returns.add(instruction);
    snapshot.put("returnInstructions", returns);
    view.setContractSnapshot(snapshot);
    contractViews.save(view);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", transactionReference);
    payload.put("instructionReference", request.instructionReference());
    payload.put("recallReference", request.recallReference());
    payload.put("quantity", request.quantity());
    payload.put("securityIsin", loanRequest.getSecurityIsin());
    payload.put("currency", loanRequest.getSettlementCurrency());
    payload.put("borrowerOrganizationReference", user.getOrganizationReference());
    payload.put("custodianOrganizationReference", loanRequest.getCustodianOrganizationReference());
    outgoing.enqueue("SECURITIES_RETURN", EventEnvelope.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("SECURITY_RETURN_SUBMITTED")
        .schemaVersion(1)
        .producerService("BORROWER")
        .destinationService("CUSTODIAN")
        .correlationId(request.instructionReference())
        .aggregateReference(request.instructionReference())
        .aggregateVersion(0L)
        .occurredAt(clock.instant())
        .senderOrganizationReference(user.getOrganizationReference())
        .receiverOrganizationReference(loanRequest.getCustodianOrganizationReference())
        .payload(payload)
        .build());
    return instruction;
  }

  @Transactional
  public Map<String, Object> submitFinalRelease(
      Authentication authentication,
      String transactionReference,
      FinalCollateralReleaseRequest request) {
    LocalUserAuthentication user = trader(authentication);
    LoanContractView view = contractViews.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    var loanRequest = requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    List<Map<String, Object>> releases =
        instructionList(snapshot.computeIfAbsent("collateralReleaseRequests", key -> new ArrayList<>()));
    for (Map<String, Object> existing : releases) {
      if (request.releaseReference().equals(existing.get("releaseReference"))) {
        return existing;
      }
      if (List.of("SUBMITTED", "PENDING", "APPROVED").contains(existing.get("status"))) {
        throw BorrowerException.conflict("A final collateral release request is already in progress");
      }
    }
    if (!"RETURNED".equals(snapshot.get("contractStatus"))
        || number(snapshot.get("outstandingQuantity")) != 0) {
      throw BorrowerException.conflict("All securities must be returned before requesting final collateral release");
    }

    Map<String, Object> release = new LinkedHashMap<>();
    release.put("releaseReference", request.releaseReference());
    release.put("status", "SUBMITTED");
    release.put("submittedAt", clock.instant().toString());
    releases.add(release);
    snapshot.put("collateralReleaseRequests", releases);
    view.setContractSnapshot(snapshot);
    contractViews.save(view);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("transactionReference", transactionReference);
    payload.put("releaseReference", request.releaseReference());
    payload.put("borrowerOrganizationReference", user.getOrganizationReference());
    payload.put("custodianOrganizationReference", loanRequest.getCustodianOrganizationReference());
    payload.put("requestReference", view.getRequestReference());
    outgoing.enqueue("COLLATERAL_RELEASE_REQUEST", EventEnvelope.builder()
        .eventId(UUID.nameUUIDFromBytes(
            ("FINAL_COLLATERAL_RELEASE_REQUESTED:" + transactionReference + ":"
                + request.releaseReference()).getBytes(StandardCharsets.UTF_8)).toString())
        .eventType("FINAL_COLLATERAL_RELEASE_REQUESTED")
        .schemaVersion(1)
        .producerService("BORROWER")
        .destinationService("CUSTODIAN")
        .correlationId(request.releaseReference())
        .aggregateReference(request.releaseReference())
        .aggregateVersion(0L)
        .occurredAt(clock.instant())
        .senderOrganizationReference(user.getOrganizationReference())
        .receiverOrganizationReference(loanRequest.getCustodianOrganizationReference())
        .payload(payload)
        .build());
    return release;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> finalReleaseRequests(
      Authentication authentication, String transactionReference) {
    LocalUserAuthentication user = trader(authentication);
    LoanContractView view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    return instructionList(view.getContractSnapshot().get("collateralReleaseRequests"));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> returnProgress(
      Authentication authentication, String transactionReference) {
    LocalUserAuthentication user = trader(authentication);
    LoanContractView view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("transactionReference", transactionReference);
    result.put("contractStatus", view.getContractSnapshot().get("contractStatus"));
    result.put("outstandingQuantity", view.getContractSnapshot().get("outstandingQuantity"));
    result.put("recalls", instructionList(view.getContractSnapshot().get("recalls")));
    result.put("returns", instructionList(view.getContractSnapshot().get("returnInstructions")));
    result.put("settlementInstructions", instructionList(
        view.getContractSnapshot().get("settlementInstructions")));
    return result;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> callProgress(
      Authentication authentication, String transactionReference, String marginCallReference) {
    LocalUserAuthentication user = trader(authentication);
    var view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    return findMarginCall(view.getContractSnapshot(), marginCallReference);
  }

  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listTopUps(
      Authentication authentication, String transactionReference, String marginCallReference) {
    LocalUserAuthentication user = trader(authentication);
    var view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    requests.findByRequestReferenceAndBorrowerOrganizationReference(
        view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Contract not found"));
    findMarginCall(view.getContractSnapshot(), marginCallReference);
    Object raw = view.getContractSnapshot().get("collateralInstructions");
    if (!(raw instanceof List<?> values)) return List.of();
    return values.stream()
        .filter(Map.class::isInstance)
        .map(value -> (Map<String, Object>) value)
        .filter(value -> marginCallReference.equals(value.get("marginCallReference")))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> instructionList(Object value) {
    if (!(value instanceof List<?>)) return new ArrayList<>();
    return (List<Map<String, Object>>) value;
  }

  private void validateContract(LoanContractView view, CashCollateralInstructionRequest request) {
    validateCashRequest(request, view.getContractSnapshot());
    Object status = view.getContractSnapshot().get("contractStatus");
    if (!"APPROVED".equals(status) && !"PENDING_SETTLEMENT".equals(status)) {
      throw BorrowerException.conflict("Contract is not eligible for initial collateral");
    }
  }

  private void validateCashRequest(
      CashCollateralInstructionRequest request, Map<String, Object> snapshot) {
    if (request == null || !"CASH".equals(request.collateralType())
        || request.amount() == null || request.amount().signum() <= 0
        || request.instructionReference() == null || request.instructionReference().isBlank()
        || request.currency() == null || !request.currency().matches("[A-Z]{3}")) {
      throw BorrowerException.invalid("Only a positive CASH collateral instruction is supported");
    }
    if (!request.currency().equals(snapshot.get("settlementCurrency"))) {
      throw BorrowerException.invalid("Collateral currency must match the contract");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> findRecall(Map<String, Object> snapshot, String reference) {
    Object raw = snapshot.get("recalls");
    if (raw instanceof List<?> values) {
      for (Object value : values) {
        if (value instanceof Map<?, ?> recall
            && reference.equals(recall.get("recallReference"))) {
          return (Map<String, Object>) recall;
        }
      }
    }
    throw BorrowerException.missing("Recall not found in this contract");
  }

  private long number(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw BorrowerException.conflict("Contract quantity projection is invalid");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> findMarginCall(
      Map<String, Object> snapshot, String marginCallReference) {
    Object raw = snapshot.get("marginCalls");
    if (raw instanceof List<?> values) {
      for (Object value : values) {
        if (value instanceof Map<?, ?> call
            && marginCallReference.equals(call.get("marginCallReference"))) {
          return (Map<String, Object>) call;
        }
      }
    }
    throw BorrowerException.missing("Margin call not found in this contract");
  }

  private LocalUserAuthentication trader(Authentication authentication) {
    if (!(authentication instanceof LocalUserAuthentication user)
        || !user.isAuthenticated()
        || user.getAuthorities().stream().noneMatch(
            authority -> authority.getAuthority().equals("ROLE_TRADER"))) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Authenticated borrower required");
    }
    return user;
  }
}
