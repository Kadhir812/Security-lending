package com.hexaware.custodian_service.service.collateral;

import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class FinalCollateralReleaseRules {
  private static final String CLOSURE_EVENT = "CONTRACT_CLOSED";

  private FinalCollateralReleaseRules() {}

  static void validateReturnedContract(LoanContract contract) {
    if (contract.getContractStatus() != LoanContractStatus.RETURNED
        || nvl(contract.getOutstandingQuantity()) != 0
        || nvl(contract.getDeliveredQuantity()) == 0
        || !Objects.equals(contract.getDeliveredQuantity(), contract.getReturnedQuantity())) {
      throw SetupException.conflict("All delivered securities must be returned and settled");
    }
  }

  static void validateCashCollateral(LoanContract contract, Collateral collateral) {
    if (!contract.getContractId().equals(collateral.getContractId())
        || !"CASH".equals(collateral.getCollateralType().name())
        || !contract.getSettlementCurrency().equals(collateral.getCurrency())
        || collateral.getPostedCashAmount() == null
        || collateral.getReleasedCashAmount() == null
        || collateral.getReleasedCashAmount().signum() < 0
        || collateral.getReleasedCashAmount().compareTo(collateral.getPostedCashAmount()) > 0
        || !List.of("POSTED", "PARTIALLY_RELEASED", "RELEASED")
            .contains(collateral.getStatus())) {
      throw SetupException.conflict(
          "Only confirmed CASH collateral in contract currency can be released");
    }
  }

  static void validateReleaseInstruction(
      SettlementInstruction instruction,
      LoanContract contract,
      Collateral collateral,
      FinalReleaseParties parties,
      BigDecimal expectedAmount) {
    if (!instruction.getContractId().equals(contract.getContractId())
        || !Objects.equals(instruction.getCollateralId(), collateral.getCollateralId())
        || !"COLLATERAL_RELEASE".equals(instruction.getInstructionType())
        || !Objects.equals(instruction.getFromOrganizationId(), parties.custodianId())
        || !Objects.equals(instruction.getToOrganizationId(), parties.borrowerId())
        || !"CUSTODIAN-CASH".equals(instruction.getFromAccount())
        || !"BORROWER-CASH".equals(instruction.getToAccount())
        || instruction.getAmount() == null
        || instruction.getAmount().compareTo(expectedAmount) != 0
        || !contract.getSettlementCurrency().equals(instruction.getCurrency())) {
      throw SetupException.conflict("Payout instruction does not match confirmed collateral");
    }
  }

  static String releaseReference(String instructionReference) {
    if (instructionReference == null || !instructionReference.startsWith("R:")) return null;
    int lastSeparator = instructionReference.lastIndexOf(':');
    return lastSeparator <= 2 ? null : instructionReference.substring(2, lastSeparator);
  }

  static String closureEventId(String transactionReference, String closureReference) {
    if (!validReleaseReference(closureReference)) {
      throw SetupException.invalid("Invalid closure reference");
    }
    return UUID.nameUUIDFromBytes(
        (CLOSURE_EVENT + ":" + transactionReference + ":" + closureReference)
            .getBytes(StandardCharsets.UTF_8)).toString();
  }

  static boolean validReference(String value) {
    return value != null && value.matches("[A-Za-z0-9._:-]{1,80}");
  }

  static boolean validReleaseReference(String value) {
    return value != null && value.matches("[A-Za-z0-9._:-]{1,48}");
  }

  static boolean validConfirmationReference(String value) {
    return value != null && value.matches("[A-Za-z0-9._:-]{1,150}");
  }

  static String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  static long number(Object value) {
    try {
      return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    } catch (RuntimeException exception) {
      throw new InvalidEnvelopeException("INVALID_FINAL_RELEASE_VERSION");
    }
  }

  static long nvl(Long value) {
    return value == null ? 0L : value;
  }

  static BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  static BigDecimal decimal(Object value) {
    try {
      return value instanceof BigDecimal amount ? amount : new BigDecimal(String.valueOf(value));
    } catch (RuntimeException exception) {
      throw SetupException.conflict("Fee balance calculation is invalid");
    }
  }
}
