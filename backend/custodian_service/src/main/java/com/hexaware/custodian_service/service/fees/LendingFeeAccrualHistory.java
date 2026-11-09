package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.client.LendingFeeCalculationRequest;
import com.hexaware.custodian_service.client.LendingFeeCalculationResponse;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LendingFeeAccrualHistory {
  private static final String CHARGE_EVENT = "LENDING_FEE_CHARGE_UPDATED";
  private static final String CHARGE_AGGREGATE = "LENDING_FEE_CHARGE";

  private final LoanRequestRepository requests;
  private final SettlementInstructionRepository settlements;
  private final OutboxEventRepository outbox;

  List<OutboxEvent> eventsForDay(String transactionReference, LocalDate day) {
    return acceptedChargeEvents(transactionReference).stream()
        .filter(event -> Objects.equals(event.getPayload().get("accrualDate"), day.toString()))
        .toList();
  }

  List<OutboxEvent> acceptedChargeEvents(String transactionReference) {
    return outbox
        .findByEventTypeAndAggregateTypeOrderByCreatedAtAsc(CHARGE_EVENT, CHARGE_AGGREGATE)
        .stream()
        .filter(
            event ->
                Objects.equals(
                    event.getPayload().get("transactionReference"), transactionReference))
        .sorted(
            (first, second) ->
                Long.compare(
                    number(first.getPayload().get("aggregateVersion")),
                    number(second.getPayload().get("aggregateVersion"))))
        .toList();
  }

  List<SettlementInstruction> settledHistory(LoanContract contract) {
    return settlements.findByContractIdAndStatusOrderBySettledAtAsc(
        contract.getContractId(), SettlementInstructionStatus.SETTLED);
  }

  void validateHistory(
      LoanContract contract,
      FeeContractParties parties,
      List<SettlementInstruction> history) {
    String isin =
        requests
            .findById(contract.getLoanRequestId())
            .orElseThrow(() -> SetupException.missing("Loan request not found"))
            .getSecurityIsin();
    long deliveries = 0;
    long returns = 0;
    for (SettlementInstruction instruction : history) {
      if ("SECURITY_DELIVERY".equals(instruction.getInstructionType())) {
        if (!isin.equals(instruction.getSecurityIsin())
            || !Objects.equals(instruction.getFromOrganizationId(), parties.lenderId())
            || !Objects.equals(instruction.getToOrganizationId(), parties.borrowerId())) {
          throw SetupException.conflict("Delivery settlement history does not match the contract");
        }
        deliveries = Math.addExact(deliveries, quantity(instruction));
      } else if ("SECURITY_RETURN".equals(instruction.getInstructionType())) {
        if (!isin.equals(instruction.getSecurityIsin())
            || !Objects.equals(instruction.getFromOrganizationId(), parties.borrowerId())
            || !Objects.equals(instruction.getToOrganizationId(), parties.lenderId())) {
          throw SetupException.conflict("Return settlement history does not match the contract");
        }
        returns = Math.addExact(returns, quantity(instruction));
      }
    }
    if (deliveries != nvl(contract.getDeliveredQuantity())
        || returns != nvl(contract.getReturnedQuantity())) {
      throw SetupException.conflict("Settled quantity history is incomplete");
    }
  }

  long accrualQuantityOnDate(List<SettlementInstruction> history, LocalDate accrualDate) {
    long quantity = 0;
    for (SettlementInstruction instruction : history) {
      if (instruction.getSettledAt() == null) continue;
      LocalDate settlementDate =
          instruction.getSettledAt().atZone(ZoneOffset.UTC).toLocalDate();
      boolean delivery = "SECURITY_DELIVERY".equals(instruction.getInstructionType());
      boolean returned = "SECURITY_RETURN".equals(instruction.getInstructionType());
      if (!delivery && !returned) continue;
      if (settlementDate.isBefore(accrualDate)
          || (settlementDate.equals(accrualDate) && delivery)) {
        quantity =
            delivery
                ? Math.addExact(quantity, quantity(instruction))
                : Math.subtractExact(quantity, quantity(instruction));
        if (quantity < 0) {
          throw SetupException.conflict("Settled return quantity exceeds delivered quantity");
        }
      }
    }
    return quantity;
  }

  String inputVersion(
      LoanContract contract, List<SettlementInstruction> history, LocalDate day, long quantity) {
    StringBuilder value =
        new StringBuilder()
            .append(contract.getTransactionReference())
            .append('|')
            .append(day)
            .append('|')
            .append(quantity)
            .append('|')
            .append(contract.getAgreedAnnualFeePct().toPlainString())
            .append('|')
            .append(contract.getSettlementCurrency())
            .append('|')
            .append(contract.getDayCountBasis());
    Instant cutoff = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    history.stream()
        .filter(instruction -> instruction.getSettledAt() != null
            && instruction.getSettledAt().isBefore(cutoff))
        .filter(instruction ->
            "SECURITY_DELIVERY".equals(instruction.getInstructionType())
                || "SECURITY_RETURN".equals(instruction.getInstructionType()))
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
                    .append(instruction.getQuantity().toPlainString())
                    .append(':')
                    .append(instruction.getSettledAt()));
    return hash(value.toString());
  }

  boolean sameFeeInputs(LoanContract first, LoanContract second) {
    return Objects.equals(first.getContractId(), second.getContractId())
        && Objects.equals(first.getLoanRequestId(), second.getLoanRequestId())
        && Objects.equals(first.getAgreedAnnualFeePct(), second.getAgreedAnnualFeePct())
        && Objects.equals(first.getSettlementCurrency(), second.getSettlementCurrency())
        && Objects.equals(first.getDayCountBasis(), second.getDayCountBasis())
        && Objects.equals(first.getDeliveredQuantity(), second.getDeliveredQuantity())
        && Objects.equals(first.getReturnedQuantity(), second.getReturnedQuantity());
  }

  void validateResult(
      LoanContract contract,
      LendingFeeCalculationRequest request,
      LendingFeeCalculationResponse response) {
    if (!"COMPLETED".equals(response.getStatus())
        || response.getResultReference() == null
        || !Objects.equals(
            response.getCalculationRequestReference(), request.getCalculationRequestReference())
        || !Objects.equals(response.getTransactionReference(), contract.getTransactionReference())
        || !Objects.equals(response.getAccrualDate(), request.getAccrualDate())
        || !Objects.equals(response.getCutoff(), request.getCutoff())
        || !Objects.equals(response.getInputVersion(), request.getInputVersion())
        || !Objects.equals(
            response.getSettledOutstandingQuantity(), request.getSettledOutstandingQuantity())
        || !Objects.equals(response.getCurrency(), contract.getSettlementCurrency())
        || !Objects.equals(response.getDayCountBasis(), request.getDayCountBasis())
        || response.getAnnualFeePercentage() == null
        || response.getAnnualFeePercentage().compareTo(request.getAnnualFeePercentage()) != 0
        || response.getAmount() == null
        || response.getAmount().signum() < 0
        || response.getAmount().scale() > 2
        || response.getMarketPrice() == null
        || response.getMarketPrice().signum() <= 0
        || response.getPriceReference() == null
        || response.getPriceReference().isBlank()
        || response.getPriceSource() == null
        || response.getPriceSource().isBlank()
        || response.getPriceTimestamp() == null
        || response.getPriceTimestamp().isAfter(request.getCutoff())
        || response.getPriceTimestamp().isBefore(request.getCutoff().minusSeconds(86400))) {
      throw SetupException.conflict("Valuation result does not match the fee accrual inputs");
    }
  }

  LocalDate firstDeliveryDate(LoanContract contract) {
    return settledHistory(contract).stream()
        .filter(instruction -> "SECURITY_DELIVERY".equals(instruction.getInstructionType()))
        .map(SettlementInstruction::getSettledAt)
        .filter(Objects::nonNull)
        .map(value -> value.atZone(ZoneOffset.UTC).toLocalDate())
        .min(LocalDate::compareTo)
        .orElse(null);
  }

  String requestReference(
      String transactionReference, LocalDate date, String inputVersion, String correctionReference) {
    return UUID.nameUUIDFromBytes(
            (transactionReference
                    + ":FEE:"
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

  long number(Object value) {
    try {
      return value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw SetupException.conflict("Fee charge aggregate version is invalid");
    }
  }

  long nvl(Long value) {
    return value == null ? 0L : value;
  }

  String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private long quantity(SettlementInstruction instruction) {
    try {
      if (instruction.getQuantity() == null || instruction.getQuantity().signum() <= 0) {
        throw SetupException.conflict("Security settlement quantity is invalid");
      }
      return instruction.getQuantity().longValueExact();
    } catch (ArithmeticException exception) {
      throw SetupException.conflict("Fee accrual requires whole-unit security settlements");
    }
  }

  private String hash(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime", exception);
    }
  }
}
