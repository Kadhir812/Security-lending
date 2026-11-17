package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.ContractStatusHistory;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.ContractStatusHistoryRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class CashRebateAccrualHistory {
  private static final String ACCRUAL_EVENT = "REBATE_ACCRUAL_UPDATED";
  private static final String ACCRUAL_AGGREGATE = "CASH_REBATE_ACCRUAL";

  private final SettlementInstructionRepository settlements;
  private final CollateralRepository collaterals;
  private final ContractStatusHistoryRepository contractHistory;
  private final OutboxEventRepository outbox;
  private final Clock clock;

  List<SettlementInstruction> settledHistory(LoanContract contract) {
    return settlements.findByContractIdAndStatusOrderBySettledAtAsc(
        contract.getContractId(), SettlementInstructionStatus.SETTLED);
  }

  List<Collateral> collateralRows(LoanContract contract) {
    return collaterals.findByContractId(contract.getContractId());
  }

  void validateHistory(
      LoanContract contract,
      FeeContractParties parties,
      List<SettlementInstruction> history,
      List<Collateral> collateralRows) {
    Map<Long, Collateral> collateralById = new LinkedHashMap<>();
    for (Collateral collateral : collateralRows) {
      if (collateral.getCollateralId() == null
          || collateral.getCollateralType() == null
          || !"CASH".equals(collateral.getCollateralType().name())
          || !Objects.equals(collateral.getCurrency(), contract.getSettlementCurrency())) {
        throw SetupException.conflict("Cash collateral history does not match the contract");
      }
      collateralById.put(collateral.getCollateralId(), collateral);
    }

    Map<Long, BigDecimal> transfers = new LinkedHashMap<>();
    Map<Long, BigDecimal> releases = new LinkedHashMap<>();
    for (SettlementInstruction instruction : history) {
      boolean transfer = "COLLATERAL_TRANSFER".equals(instruction.getInstructionType());
      boolean release = "COLLATERAL_RELEASE".equals(instruction.getInstructionType());
      if (!transfer && !release) continue;
      if (instruction.getSettledAt() == null
          || instruction.getCollateralId() == null
          || instruction.getAmount() == null
          || instruction.getAmount().signum() <= 0
          || instruction.getAmount().scale() > 2
          || !Objects.equals(instruction.getCurrency(), contract.getSettlementCurrency())) {
        throw SetupException.conflict("Settled cash collateral instruction is incomplete");
      }
      Collateral collateral = collateralById.get(instruction.getCollateralId());
      if (collateral == null
          || !Objects.equals(collateral.getContractId(), contract.getContractId())) {
        throw SetupException.conflict("Cash collateral instruction references another contract");
      }
      boolean partiesMatch =
          transfer
              ? Objects.equals(instruction.getFromOrganizationId(), parties.borrowerId())
                  && Objects.equals(instruction.getToOrganizationId(), parties.custodianId())
              : Objects.equals(instruction.getFromOrganizationId(), parties.custodianId())
                  && Objects.equals(instruction.getToOrganizationId(), parties.borrowerId());
      if (!partiesMatch) {
        throw SetupException.conflict("Cash collateral settlement parties do not match the contract");
      }
      Map<Long, BigDecimal> totals = transfer ? transfers : releases;
      totals.merge(instruction.getCollateralId(), instruction.getAmount(), BigDecimal::add);
    }

    for (Collateral collateral : collateralRows) {
      BigDecimal posted = nvl(collateral.getPostedCashAmount());
      BigDecimal released = nvl(collateral.getReleasedCashAmount());
      if (posted.signum() < 0 || released.signum() < 0 || released.compareTo(posted) > 0) {
        throw SetupException.conflict("Cash collateral balance is invalid");
      }
      if (!"PENDING".equals(collateral.getStatus())
          && !"FAILED".equals(collateral.getStatus())
          && posted.compareTo(nvl(transfers.get(collateral.getCollateralId()))) != 0) {
        throw SetupException.conflict("Settled cash transfer history is incomplete");
      }
      if (released.compareTo(nvl(releases.get(collateral.getCollateralId()))) != 0) {
        throw SetupException.conflict("Settled cash release history is incomplete");
      }
    }
  }

  BigDecimal cashBalanceOnDate(List<SettlementInstruction> history, LocalDate accrualDate) {
    BigDecimal balance = BigDecimal.ZERO;
    for (SettlementInstruction instruction : history) {
      if (instruction.getSettledAt() == null) continue;
      LocalDate settlementDate =
          instruction.getSettledAt().atZone(clock.getZone()).toLocalDate();
      if ("COLLATERAL_TRANSFER".equals(instruction.getInstructionType())
          && !settlementDate.isAfter(accrualDate)) {
        balance = balance.add(instruction.getAmount());
      } else if ("COLLATERAL_RELEASE".equals(instruction.getInstructionType())
          && settlementDate.isBefore(accrualDate)) {
        balance = balance.subtract(instruction.getAmount());
      }
      if (balance.signum() < 0) {
        throw SetupException.conflict("Settled cash release exceeds settled cash collateral");
      }
    }
    return balance;
  }

  LocalDate firstTransferDate(List<SettlementInstruction> history) {
    return history.stream()
        .filter(row -> "COLLATERAL_TRANSFER".equals(row.getInstructionType()))
        .map(SettlementInstruction::getSettledAt)
        .filter(Objects::nonNull)
        .map(value -> value.atZone(clock.getZone()).toLocalDate())
        .min(LocalDate::compareTo)
        .orElse(null);
  }

  LocalDate contractCloseDate(LoanContract contract) {
    if (contract.getContractStatus() != LoanContractStatus.CLOSED) return null;
    List<ContractStatusHistory> closed =
        contractHistory.findByContractIdAndNewStatusOrderByChangedAtAsc(
            contract.getContractId(), LoanContractStatus.CLOSED.name());
    if (closed.isEmpty()) {
      throw SetupException.conflict("Closed contract has no recorded close date");
    }
    return closed.get(0).getChangedAt().atZone(clock.getZone()).toLocalDate();
  }

  List<OutboxEvent> eventsForDay(String transactionReference, LocalDate day) {
    return acceptedEvents(transactionReference).stream()
        .filter(
            event ->
                Objects.equals(businessPayload(event).get("accrualDate"), day.toString()))
        .toList();
  }

  List<OutboxEvent> acceptedEvents(String transactionReference) {
    List<OutboxEvent> candidates =
        outbox
            .findByEventTypeAndAggregateTypeAndAggregateReferenceOrderByAggregateVersionAscCreatedAtAsc(
                ACCRUAL_EVENT, ACCRUAL_AGGREGATE, transactionReference)
            .stream()
            .filter(
                event ->
                    Objects.equals(
                        businessPayload(event).get("transactionReference"),
                        transactionReference))
            .toList();
    Map<String, OutboxEvent> latestByItem = new LinkedHashMap<>();
    List<OutboxEvent> accepted = new ArrayList<>();
    for (OutboxEvent event : candidates) {
      Map<String, Object> payload = businessPayload(event);
      String itemKey = text(payload.get("itemKey"));
      if (itemKey == null) itemKey = text(payload.get("accrualDate"));
      if (itemKey == null) itemKey = event.getEventId();
      OutboxEvent previous = latestByItem.get(itemKey);
      String supersedes = text(payload.get("supersedesResultReference"));
      String previousResult =
          previous == null ? null : text(businessPayload(previous).get("resultReference"));
      if (!Objects.equals(supersedes, previousResult)) {
        log.warn("Ignoring unlinked rebate accrual event {} for {}", event.getEventId(),
            transactionReference);
        continue;
      }
      accepted.add(event);
      latestByItem.put(itemKey, event);
    }
    return accepted;
  }

  Map<String, Object> businessPayload(OutboxEvent event) {
    if (event.getPayload() == null) {
      throw SetupException.conflict("Rebate accrual event payload is missing");
    }
    if (!event.getPayload().containsKey("payload")) return event.getPayload();
    Object nestedPayload = event.getPayload().get("payload");
    if (!(nestedPayload instanceof Map<?, ?> nested)) {
      throw SetupException.conflict("Rebate accrual event payload is invalid");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : nested.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw SetupException.conflict("Rebate accrual payload contains an invalid key");
      }
      payload.put(key, entry.getValue());
    }
    return payload;
  }

  long nextAggregateVersion(String transactionReference) {
    try {
      return Math.addExact(
          outbox.findMaxAggregateVersion(
              ACCRUAL_EVENT, ACCRUAL_AGGREGATE, transactionReference),
          1L);
    } catch (ArithmeticException exception) {
      throw SetupException.conflict("Rebate aggregate version is exhausted");
    }
  }

  String inputVersion(
      LoanContract contract,
      List<SettlementInstruction> history,
      LocalDate date,
      BigDecimal balance,
      LocalDate agreementEffectiveDate,
      LocalDate agreementExpiryDate,
      LocalDate contractCloseDate) {
    StringBuilder value =
        new StringBuilder()
            .append(contract.getTransactionReference())
            .append('|')
            .append(date)
            .append('|')
            .append(balance.toPlainString())
            .append('|')
            .append(contract.getAgreedAnnualRebatePct().toPlainString())
            .append('|')
            .append(contract.getSettlementCurrency())
            .append('|')
            .append(agreementEffectiveDate)
            .append('|')
            .append(agreementExpiryDate)
            .append('|')
            .append(contractCloseDate);
    Instant cutoff = date.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    history.stream()
        .filter(instruction -> instruction.getSettledAt() != null
            && instruction.getSettledAt().isBefore(cutoff))
        .filter(
            instruction ->
                "COLLATERAL_TRANSFER".equals(instruction.getInstructionType())
                    || "COLLATERAL_RELEASE".equals(instruction.getInstructionType()))
        .forEach(
            instruction ->
                value
                    .append('|')
                    .append(instruction.getInstructionId())
                    .append(':')
                    .append(instruction.getVersion())
                    .append(':')
                    .append(instruction.getInstructionType())
                    .append(':')
                    .append(instruction.getAmount().toPlainString())
                    .append(':')
                    .append(instruction.getSettledAt()));
    return hash(value.toString());
  }

  String requestReference(
      String transactionReference, LocalDate date, String inputVersion, String correctionReference) {
    return UUID.nameUUIDFromBytes(
            (transactionReference
                    + ":REBATE:"
                    + date
                    + ":"
                    + inputVersion
                    + ":"
                    + (correctionReference == null ? "AUTO" : correctionReference))
                .getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  BigDecimal decimal(Object value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime", exception);
    }
  }
}
