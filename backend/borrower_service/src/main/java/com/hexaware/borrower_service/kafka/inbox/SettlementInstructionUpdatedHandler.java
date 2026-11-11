package com.hexaware.borrower_service.kafka.inbox;

import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SettlementInstructionUpdatedHandler implements MessageHandler {
  private final LoanContractViewRepository contracts;
  private final LoanRequestRepository requests;
  private final LoanRequestFixMessageBuilder fixMessages;
  private final Clock clock;

  @Override
  public String eventType() {
    return "SETTLEMENT_INSTRUCTION_UPDATED";
  }

  @Override
  @Transactional
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    if (payload.containsKey("fixMessage")) {
      if (payload.size() != 1 || !(payload.get("fixMessage") instanceof String fixMessage)) {
        throw invalid("INVALID_FIX_U2_SETTLEMENT_CONFIRMATION");
      }
      try {
        payload = fixMessages.parseSettlementStatusAndValidate(event, fixMessage);
      } catch (IllegalArgumentException exception) {
        throw invalid("INVALID_FIX_U2_SETTLEMENT_CONFIRMATION");
      }
    }
    String transactionReference = text(payload.get("transactionReference"));
    String instructionReference = text(payload.get("instructionReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    String instructionType = text(payload.get("instructionType"));
    String settlementStatus = text(payload.get("settlementStatus"));
    long incomingVersion = integer(event.getAggregateVersion());
    boolean collateralRelease = "COLLATERAL_RELEASE".equals(instructionType);
    if (!"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || integer(payload.get("payloadVersion")) != 1
        || !Objects.equals(event.getAggregateReference(), instructionReference)
        || !Objects.equals(event.getCorrelationId(), instructionReference)
        || !Objects.equals(event.getSenderOrganizationReference(), custodianReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), borrowerReference)
        || integer(payload.get("aggregateVersion")) != incomingVersion
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || transactionReference == null
        || instructionReference == null
        || !instructionReference.matches("[A-Za-z0-9._:-]{1,80}")
        || borrowerReference == null
        || custodianReference == null
        || !List.of(
                "COLLATERAL_TRANSFER", "SECURITY_DELIVERY", "SECURITY_RETURN",
                "COLLATERAL_RELEASE")
            .contains(instructionType)
        || !(collateralRelease
            ? List.of("PENDING", "SETTLED", "FAILED").contains(settlementStatus)
            : List.of("SETTLED", "FAILED").contains(settlementStatus))) {
      throw invalid("SETTLEMENT_UPDATE_ENVELOPE_MISMATCH");
    }
    if (collateralRelease) {
      boolean pendingValid = "PENDING".equals(settlementStatus)
          && text(payload.get("externalConfirmationReference")) == null
          && text(payload.get("failureReason")) == null
          && Boolean.FALSE.equals(payload.get("simulatedConfirmation"));
      boolean settledValid = "SETTLED".equals(settlementStatus)
          && text(payload.get("externalConfirmationReference")) != null
          && Boolean.TRUE.equals(payload.get("simulatedConfirmation"));
      boolean failedValid = "FAILED".equals(settlementStatus)
          && text(payload.get("failureReason")) != null
          && Boolean.FALSE.equals(payload.get("simulatedConfirmation"));
      if ((!pendingValid && !settledValid && !failedValid) || incomingVersion < 1) {
        throw invalid("COLLATERAL_RELEASE_CONFIRMATION_INVALID");
      }
    } else if ("SECURITY_RETURN".equals(instructionType)) {
      boolean settledConfirmationValid = "SETTLED".equals(settlementStatus)
          && text(payload.get("externalConfirmationReference")) != null
          && Boolean.TRUE.equals(payload.get("simulatedConfirmation"));
      boolean failedConfirmationValid = "FAILED".equals(settlementStatus)
          && text(payload.get("failureReason")) != null
          && Boolean.FALSE.equals(payload.get("simulatedConfirmation"));
      if ((!settledConfirmationValid && !failedConfirmationValid) || incomingVersion < 2) {
        throw invalid("RETURN_SETTLEMENT_CONFIRMATION_INVALID");
      }
    } else if (!"SETTLED".equals(settlementStatus)
        || text(payload.get("externalConfirmationReference")) == null
        || !Boolean.TRUE.equals(payload.get("simulatedConfirmation"))) {
      throw invalid("SETTLEMENT_CONFIRMATION_INVALID");
    }

    LoanContractView view = contracts.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> invalid("UNKNOWN_CONTRACT"));
    LoanRequest request = requests.findByRequestReference(view.getRequestReference())
        .orElseThrow(() -> invalid("UNKNOWN_LOAN_REQUEST"));
    if (!borrowerReference.equals(request.getBorrowerOrganizationReference())
        || !custodianReference.equals(request.getCustodianOrganizationReference())
        || !request.getSettlementCurrency().equals(payload.get("currency"))) {
      throw invalid("SETTLEMENT_UPDATE_PARTIES_OR_CURRENCY_MISMATCH");
    }

    Map<String, Object> snapshot = new LinkedHashMap<>(view.getContractSnapshot());
    String status = text(payload.get("instructionStatus"));
    if (!Objects.equals(status, settlementStatus)) {
      throw invalid("SETTLEMENT_UPDATE_STATUS_INVALID");
    }
    if (collateralRelease) {
      if (!applyCollateralRelease(snapshot, payload, instructionReference, incomingVersion)) return;
    } else if ("SECURITY_RETURN".equals(instructionType)) {
      applyReturnOutcome(snapshot, payload, instructionReference, incomingVersion, request);
    } else if ("COLLATERAL_TRANSFER".equals(instructionType)) {
      applyCashConfirmation(snapshot, payload, instructionReference, incomingVersion, status);
    } else {
      applySecurityDelivery(snapshot, payload, instructionReference, incomingVersion, status, request);
    }
    view.setContractSnapshot(snapshot);
    contracts.save(view);
  }

  private void applyReturnOutcome(
      Map<String, Object> snapshot,
      Map<String, Object> payload,
      String instructionReference,
      long incomingVersion,
      LoanRequest request) {
    List<Map<String, Object>> returns = mapList(snapshot.get("returnInstructions"));
    Map<String, Object> instruction = find(returns, instructionReference);
    if (instruction == null) throw invalid("SETTLEMENT_FOR_UNKNOWN_RETURN_INSTRUCTION");
    long quantity = integer(payload.get("quantity"));
    String status = text(payload.get("settlementStatus"));
    if (quantity <= 0
        || quantity != integer(instruction.get("quantity"))
        || !Objects.equals(request.getSecurityIsin(), payload.get("securityIsin"))
        || !Objects.equals(instruction.get("securityIsin"), payload.get("securityIsin"))
        || !Objects.equals(request.getSettlementCurrency(), payload.get("currency"))
        || !Objects.equals(instruction.get("recallReference"), payload.get("recallReference"))) {
      throw invalid("RETURN_SETTLEMENT_DOES_NOT_MATCH_SUBMISSION");
    }
    long currentVersion = number(instruction.get("settlementAggregateVersion"));
    if (incomingVersion < currentVersion) return;
    if (incomingVersion == currentVersion && currentVersion > 0) {
      if (Objects.equals(instruction.get("settlementStatus"), status)
          && Objects.equals(instruction.get("externalConfirmationReference"),
              payload.get("externalConfirmationReference"))) {
        return;
      }
      throw invalid("CONFLICTING_RETURN_SETTLEMENT_VERSION");
    }
    if (incomingVersion != currentVersion + 1
        && !(currentVersion == 0 && incomingVersion == 2)) {
      throw invalid("RETURN_SETTLEMENT_VERSION_GAP");
    }

    instruction.put("status", status);
    instruction.put("settlementStatus", status);
    instruction.put("settlementAggregateVersion", incomingVersion);
    instruction.put("settledAt", payload.get("settledAt"));
    instruction.put("externalConfirmationReference",
        payload.get("externalConfirmationReference"));
    instruction.put("failureReason", payload.get("failureReason"));
    instruction.put("simulatedConfirmation", payload.get("simulatedConfirmation"));
    List<Map<String, Object>> settlementInstructions =
        mapList(snapshot.get("settlementInstructions"));
    Map<String, Object> settlement = find(settlementInstructions, instructionReference);
    if (settlement == null) {
      settlement = new LinkedHashMap<>();
      settlementInstructions.add(settlement);
    }
    settlement.putAll(instruction);
    settlement.put("instructionType", "SECURITY_RETURN");
    settlement.put("instructionReference", instructionReference);
    snapshot.put("returnInstructions", returns);
    snapshot.put("settlementInstructions", settlementInstructions);

    if ("SETTLED".equals(status)) {
      long contractVersion = integer(payload.get("contractVersion"));
      long currentContractVersion = number(snapshot.get("settlementContractVersion"));
      long returnedQuantity = integer(payload.get("returnedQuantity"));
      long outstandingQuantity = integer(payload.get("outstandingQuantity"));
      long deliveredQuantity = integer(payload.get("deliveredQuantity"));
      if (deliveredQuantity != request.getQuantity()
          || returnedQuantity < quantity
          || outstandingQuantity != deliveredQuantity - returnedQuantity
          || (!"ON_LOAN".equals(payload.get("contractStatus"))
              && !"RETURNED".equals(payload.get("contractStatus")))) {
        throw invalid("RETURNED_CONTRACT_BALANCE_MISMATCH");
      }
      if (contractVersion >= currentContractVersion) {
        snapshot.put("settlementContractVersion", contractVersion);
        snapshot.put("contractStatus", payload.get("contractStatus"));
        snapshot.put("deliveredQuantity", deliveredQuantity);
        snapshot.put("returnedQuantity", returnedQuantity);
        snapshot.put("outstandingQuantity", outstandingQuantity);
      }
    }
  }

  private void applyCashConfirmation(
      Map<String, Object> snapshot,
      Map<String, Object> payload,
      String instructionReference,
      long incomingVersion,
      String status) {
    List<Map<String, Object>> instructions = mapList(snapshot.get("collateralInstructions"));
    Map<String, Object> instruction = find(instructions, instructionReference);
    if (instruction == null) throw invalid("SETTLEMENT_FOR_UNKNOWN_COLLATERAL_INSTRUCTION");
    long currentVersion = number(instruction.get("settlementAggregateVersion"));
    if (incomingVersion < currentVersion) return;
    if (incomingVersion == currentVersion && currentVersion > 0) {
      if ("POSTED".equals(instruction.get("status"))
          && Objects.equals(instruction.get("externalConfirmationReference"),
              payload.get("externalConfirmationReference"))) {
        return;
      }
      throw invalid("CONFLICTING_SETTLEMENT_INSTRUCTION_VERSION");
    }
    if ((incomingVersion != currentVersion + 1
            && !(currentVersion == 0 && incomingVersion == 2))
        || decimal(instruction.get("amount")).compareTo(decimal(payload.get("amount"))) != 0
        || !Objects.equals(instruction.get("currency"), payload.get("currency"))
        || !"CASH".equals(payload.get("collateralType"))) {
      throw invalid("SETTLEMENT_DOES_NOT_MATCH_COLLATERAL_INSTRUCTION");
    }
    instruction.put("status", "POSTED");
    instruction.put("settlementStatus", status);
    instruction.put("settlementAggregateVersion", incomingVersion);
    instruction.put("settledAt", payload.get("settledAt"));
    instruction.put("externalConfirmationReference", payload.get("externalConfirmationReference"));
    instruction.put("simulatedConfirmation", true);
    snapshot.put("collateralInstructions", instructions);
    Long contractVersion = optionalInteger(payload.get("contractVersion"));
    long currentContractVersion = number(snapshot.get("settlementContractVersion"));
    if ((contractVersion != null && contractVersion >= currentContractVersion)
        || (contractVersion == null && snapshot.get("settlementContractVersion") == null)) {
      if (contractVersion != null) snapshot.put("settlementContractVersion", contractVersion);
      snapshot.put("contractStatus", payload.get("contractStatus"));
      snapshot.put("confirmedCashCollateral", payload.get("confirmedCashCollateral"));
    }
  }

  private boolean applyCollateralRelease(
      Map<String, Object> snapshot,
      Map<String, Object> payload,
      String instructionReference,
      long incomingVersion) {
    String releaseReference = text(payload.get("releaseReference"));
    int lastSeparator = instructionReference.lastIndexOf(':');
    String instructionReleaseReference =
        instructionReference.startsWith("R:") && lastSeparator > 2
            ? instructionReference.substring(2, lastSeparator)
            : null;
    BigDecimal amount = decimal(payload.get("amount"));
    long instructionCount = integer(payload.get("releaseInstructionCount"));
    if (releaseReference == null
        || !releaseReference.equals(instructionReleaseReference)
        || amount.signum() <= 0
        || instructionCount <= 0) {
      throw invalid("COLLATERAL_RELEASE_INSTRUCTION_INVALID");
    }

    List<Map<String, Object>> instructions =
        mapList(snapshot.get("collateralReleaseInstructions"));
    Map<String, Object> instruction = find(instructions, instructionReference);
    long currentVersion = instruction == null
        ? 0 : number(instruction.get("settlementAggregateVersion"));
    String status = text(payload.get("settlementStatus"));
    if (incomingVersion < currentVersion) return false;
    if (incomingVersion == currentVersion && currentVersion > 0) {
      if (Objects.equals(instruction.get("settlementStatus"), status)
          && Objects.equals(instruction.get("externalConfirmationReference"),
              payload.get("externalConfirmationReference"))
          && Objects.equals(instruction.get("failureReason"), payload.get("failureReason"))) {
        return false;
      }
      throw invalid("CONFLICTING_COLLATERAL_RELEASE_VERSION");
    }
    if (incomingVersion != currentVersion + 1) {
      throw invalid("COLLATERAL_RELEASE_VERSION_GAP");
    }
    if (instruction == null) {
      instruction = new LinkedHashMap<>();
      instructions.add(instruction);
    } else if (decimal(instruction.get("amount")).compareTo(amount) != 0
        || !Objects.equals(instruction.get("currency"), payload.get("currency"))
        || !Objects.equals(instruction.get("releaseReference"), releaseReference)) {
      throw invalid("COLLATERAL_RELEASE_INPUT_CHANGED");
    }
    instruction.put("instructionReference", instructionReference);
    instruction.put("releaseReference", releaseReference);
    instruction.put("amount", amount.toPlainString());
    instruction.put("currency", payload.get("currency"));
    instruction.put("status", status);
    instruction.put("settlementStatus", status);
    instruction.put("settlementAggregateVersion", incomingVersion);
    instruction.put("settledAt", payload.get("settledAt"));
    instruction.put("externalConfirmationReference",
        payload.get("externalConfirmationReference"));
    instruction.put("failureReason", payload.get("failureReason"));
    instruction.put("simulatedConfirmation", payload.get("simulatedConfirmation"));
    instruction.put("releaseInstructionCount", instructionCount);
    snapshot.put("collateralReleaseInstructions", instructions);

    List<Map<String, Object>> releases =
        mapList(snapshot.get("collateralReleaseRequests"));
    Map<String, Object> request = findRelease(releases, releaseReference);
    if (request == null) throw invalid("SETTLEMENT_FOR_UNKNOWN_RELEASE_REQUEST");
    String releaseStatus = releaseStatus(instructions, releaseReference, instructionCount);
    if ("CLOSED".equals(snapshot.get("contractStatus"))
        && !"SETTLED".equals(releaseStatus)) {
      releaseStatus = "COMPLETED";
    }
    request.put("status", releaseStatus);
    request.put("updatedAt", payload.get("settledAt"));
    BigDecimal total = instructions.stream()
        .filter(row -> releaseReference.equals(row.get("releaseReference")))
        .map(row -> decimal(row.get("amount")))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    request.put("amount", total.toPlainString());
    request.put("currency", payload.get("currency"));
    snapshot.put("collateralReleaseRequests", releases);
    if ("SETTLED".equals(status) && !"CLOSED".equals(snapshot.get("contractStatus"))) {
      BigDecimal heldCash = decimal(snapshot.get("confirmedCashCollateral"));
      BigDecimal remainingCash = heldCash.subtract(amount);
      if (remainingCash.signum() < 0) {
        throw invalid("COLLATERAL_RELEASE_EXCEEDS_BORROWER_BALANCE");
      }
      snapshot.put("confirmedCashCollateral", remainingCash.toPlainString());
    }

    List<Map<String, Object>> settlementInstructions = mapList(snapshot.get("settlementInstructions"));
    Map<String, Object> settlement = find(settlementInstructions, instructionReference);
    if (settlement == null) {
      settlement = new LinkedHashMap<>();
      settlementInstructions.add(settlement);
    }
    settlement.putAll(instruction);
    settlement.put("instructionType", "COLLATERAL_RELEASE");
    snapshot.put("settlementInstructions", settlementInstructions);
    return true;
  }

  private String releaseStatus(
      List<Map<String, Object>> instructions, String releaseReference, long instructionCount) {
    List<Map<String, Object>> related = instructions.stream()
        .filter(row -> releaseReference.equals(row.get("releaseReference")))
        .toList();
    if (related.stream().anyMatch(row -> "FAILED".equals(row.get("settlementStatus")))) {
      return "FAILED";
    }
    long settled = related.stream()
        .filter(row -> "SETTLED".equals(row.get("settlementStatus")))
        .count();
    return settled >= instructionCount ? "SETTLED" : "APPROVED";
  }

  private Map<String, Object> findRelease(
      List<Map<String, Object>> releases, String reference) {
    return releases.stream()
        .filter(row -> reference.equals(row.get("releaseReference")))
        .findFirst()
        .orElse(null);
  }

  private void applySecurityDelivery(
      Map<String, Object> snapshot,
      Map<String, Object> payload,
      String instructionReference,
      long incomingVersion,
      String status,
      LoanRequest request) {
    if (!Objects.equals(request.getSecurityIsin(), payload.get("securityIsin"))
        || integer(payload.get("quantity")) != request.getQuantity()
        || integer(payload.get("deliveredQuantity")) != request.getQuantity()
        || integer(payload.get("outstandingQuantity")) != request.getQuantity()
        || !Objects.equals(request.getSettlementCurrency(), payload.get("currency"))
        || !"ON_LOAN".equals(payload.get("contractStatus"))) {
      throw invalid("SECURITY_DELIVERY_DOES_NOT_MATCH_APPROVED_CONTRACT");
    }
    List<Map<String, Object>> instructions = mapList(snapshot.get("settlementInstructions"));
    Map<String, Object> existing = find(instructions, instructionReference);
    if (existing != null) {
      long currentVersion = number(existing.get("settlementAggregateVersion"));
      if (incomingVersion < currentVersion) return;
      if (incomingVersion == currentVersion && currentVersion > 0) {
        if ("SETTLED".equals(existing.get("settlementStatus"))
            && Objects.equals(existing.get("externalConfirmationReference"),
                payload.get("externalConfirmationReference"))) {
          return;
        }
        throw invalid("CONFLICTING_SETTLEMENT_INSTRUCTION_VERSION");
      }
      if (incomingVersion != currentVersion + 1) {
        throw invalid("SETTLEMENT_INSTRUCTION_VERSION_GAP");
      }
      existing.putAll(payload);
      existing.put("settlementAggregateVersion", incomingVersion);
      existing.put("settledAt", payload.get("settledAt"));
      existing.put("settlementStatus", status);
      existing.put("simulatedConfirmation", true);
    } else {
      if (incomingVersion != 1) throw invalid("SETTLEMENT_INSTRUCTION_VERSION_GAP");
      Map<String, Object> instruction = new LinkedHashMap<>(payload);
      instruction.put("settlementAggregateVersion", incomingVersion);
      instruction.put("settledAt", payload.get("settledAt"));
      instruction.put("settlementStatus", status);
      instruction.put("simulatedConfirmation", true);
      instructions.add(instruction);
    }
    snapshot.put("settlementInstructions", instructions);
    Long contractVersion = optionalInteger(payload.get("contractVersion"));
    long currentContractVersion = number(snapshot.get("settlementContractVersion"));
    if ((contractVersion != null && contractVersion >= currentContractVersion)
        || (contractVersion == null && snapshot.get("settlementContractVersion") == null)) {
      if (contractVersion != null) snapshot.put("settlementContractVersion", contractVersion);
      snapshot.put("contractStatus", payload.get("contractStatus"));
      snapshot.put("deliveredQuantity", payload.get("deliveredQuantity"));
      snapshot.put("outstandingQuantity", payload.get("outstandingQuantity"));
      snapshot.put("confirmedCashCollateral", payload.get("confirmedCashCollateral"));
    }
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String reference) {
    return rows.stream()
        .filter(row -> reference.equals(row.get("instructionReference")))
        .findFirst()
        .orElse(null);
  }

  private long number(Object value) {
    return value == null ? 0 : integer(value);
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
      throw invalid("INVALID_SETTLEMENT_AMOUNT");
    }
  }

  private long integer(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_SETTLEMENT_VERSION_OR_QUANTITY");
    }
  }

  private Long optionalInteger(Object value) {
    if (value == null) return null;
    return integer(value);
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
