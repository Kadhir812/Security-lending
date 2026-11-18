package com.hexaware.borrower_service.service.cash;

import com.hexaware.borrower_service.dto.request.BorrowerExternalCashMovementRequest;
import com.hexaware.borrower_service.dto.response.BorrowerCashAccountResponse;
import com.hexaware.borrower_service.dto.response.BorrowerCashTransactionResponse;
import com.hexaware.borrower_service.dto.response.PageResponse;
import com.hexaware.borrower_service.entity.BorrowerCashAccount;
import com.hexaware.borrower_service.entity.BorrowerCashTransaction;
import com.hexaware.borrower_service.enums.BorrowerCashAccountStatus;
import com.hexaware.borrower_service.enums.BorrowerCashActivityType;
import com.hexaware.borrower_service.enums.BorrowerCashTransactionType;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.BorrowerCashAccountRepository;
import com.hexaware.borrower_service.repository.BorrowerCashTransactionRepository;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BorrowerCashService {
  private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
  private static final Pattern IDEMPOTENCY_KEY =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}");
  private static final Pattern TRANSACTION_REFERENCE =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,149}");
  private final BorrowerCashAccountRepository accounts;
  private final BorrowerCashTransactionRepository transactions;
  private final LoanRequestSubmissionValidator validator;
  private final Clock clock;

  @Transactional
  public BorrowerCashAccountResponse openAccount(
      Authentication authentication, String currency) {
    String organizationReference =
        validator.requireBorrower(authentication).getOrganizationReference();
    validateCurrency(currency);
    accounts.createIfAbsent(organizationReference, currency);
    BorrowerCashAccount account =
        accounts
            .findByBorrowerOrganizationReferenceAndCurrency(organizationReference, currency)
            .orElseThrow(() -> BorrowerException.conflict("Cash account could not be opened"));
    return accountResponse(account);
  }

  @Transactional(readOnly = true)
  public List<BorrowerCashAccountResponse> listAccounts(Authentication authentication) {
    String organizationReference =
        validator.requireBorrower(authentication).getOrganizationReference();
    return accounts
        .findByBorrowerOrganizationReferenceOrderByCurrencyAsc(organizationReference)
        .stream()
        .map(this::accountResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public BorrowerCashAccountResponse getAccount(
      Authentication authentication, String currency) {
    String organizationReference =
        validator.requireBorrower(authentication).getOrganizationReference();
    validateCurrency(currency);
    BorrowerCashAccount account =
        accounts
            .findByBorrowerOrganizationReferenceAndCurrency(organizationReference, currency)
            .orElseThrow(() -> BorrowerException.missing("Cash account not found"));
    return accountResponse(account);
  }

  @Transactional(readOnly = true)
  public PageResponse<BorrowerCashTransactionResponse> listTransactions(
      Authentication authentication, String currency, int page, int size) {
    String organizationReference =
        validator.requireBorrower(authentication).getOrganizationReference();
    validateCurrency(currency);
    if (page < 0 || size < 1 || size > 100) {
      throw BorrowerException.invalid("Page must be non-negative and size must be from 1 to 100");
    }
    BorrowerCashAccount account =
        accounts
            .findByBorrowerOrganizationReferenceAndCurrency(organizationReference, currency)
            .orElseThrow(() -> BorrowerException.missing("Cash account not found"));
    return PageResponse.from(
        transactions
            .findByAccount_AccountIdOrderByTransactionTimeDesc(
                account.getAccountId(), PageRequest.of(page, size))
            .map(transaction -> transactionResponse(transaction, currency)));
  }

  @Transactional
  public BorrowerCashTransactionResponse recordExternalMovement(
      Authentication authentication,
      String currency,
      String idempotencyKey,
      BorrowerExternalCashMovementRequest request) {
    var borrower = validator.requireBorrower(authentication);
    if (request == null
        || idempotencyKey == null
        || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
      throw BorrowerException.invalid("A valid Idempotency-Key and cash movement are required");
    }
    return applyTransaction(
        borrower.getOrganizationReference(),
        currency,
        "CASH:EXTERNAL:" + idempotencyKey,
        request.transactionType(),
        BorrowerCashActivityType.EXTERNAL_CASH_MOVEMENT,
        request.amount(),
        "EXTERNAL",
        request.externalReference(),
        request.description(),
        request.transactionTime(),
        borrower.getUserId());
  }

  @Transactional
  public BorrowerCashTransactionResponse recordTransaction(
      String organizationReference,
      String currency,
      String transactionReference,
      BorrowerCashTransactionType transactionType,
      BorrowerCashActivityType activityType,
      BigDecimal amount,
      String relatedReferenceType,
      String relatedReference,
      String description,
      Instant transactionTime) {
    return applyTransaction(
        organizationReference,
        currency,
        transactionReference,
        transactionType,
        activityType,
        amount,
        relatedReferenceType,
        relatedReference,
        description,
        transactionTime,
        null);
  }

  private BorrowerCashTransactionResponse applyTransaction(
      String organizationReference,
      String currency,
      String transactionReference,
      BorrowerCashTransactionType transactionType,
      BorrowerCashActivityType activityType,
      BigDecimal amount,
      String relatedReferenceType,
      String relatedReference,
      String description,
      Instant transactionTime,
      Long recordedByUserId) {
    validateMovement(
        organizationReference,
        currency,
        transactionReference,
        transactionType,
        activityType,
        amount,
        relatedReferenceType,
        relatedReference,
        description,
        transactionTime);

    accounts.createIfAbsent(organizationReference, currency);
    BorrowerCashAccount account =
        accounts
            .findLockedByOrganizationAndCurrency(organizationReference, currency)
            .orElseThrow(() -> BorrowerException.conflict("Cash account could not be opened"));

    BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
    BorrowerCashTransaction existing =
        transactions.findByTransactionReference(transactionReference).orElse(null);
    if (existing != null) {
      if (sameTransaction(
          existing,
          account,
          transactionType,
          activityType,
          normalizedAmount,
          relatedReferenceType,
          relatedReference,
          description,
          transactionTime)) {
        return transactionResponse(existing, currency);
      }
      throw BorrowerException.conflict(
          "Cash transaction reference was reused with different details");
    }

    if (account.getStatus() != BorrowerCashAccountStatus.ACTIVE) {
      throw BorrowerException.conflict("Cash account is not active");
    }
    BigDecimal newBalance =
        transactionType == BorrowerCashTransactionType.CREDIT
            ? account.getCashBalance().add(normalizedAmount)
            : account.getCashBalance().subtract(normalizedAmount);
    if (newBalance.signum() < 0) {
      throw BorrowerException.conflict("Cash transaction exceeds the account balance");
    }
    if (newBalance.precision() > 20) {
      throw BorrowerException.invalid("Cash account balance exceeds supported precision");
    }

    Instant recordedAt = clock.instant();
    account.setCashBalance(newBalance);
    account.setUpdatedAt(recordedAt);
    accounts.save(account);
    BorrowerCashTransaction saved =
        transactions.save(
            BorrowerCashTransaction.builder()
                .account(account)
                .recordedByUserId(recordedByUserId)
                .transactionReference(transactionReference)
                .transactionType(transactionType)
                .activityType(activityType.name())
                .amount(normalizedAmount)
                .relatedReferenceType(relatedReferenceType)
                .relatedReference(relatedReference)
                .description(description)
                .transactionTime(transactionTime)
                .recordedAt(recordedAt)
                .build());
    return transactionResponse(saved, currency);
  }

  private void validateMovement(
      String organizationReference,
      String currency,
      String transactionReference,
      BorrowerCashTransactionType transactionType,
      BorrowerCashActivityType activityType,
      BigDecimal amount,
      String relatedReferenceType,
      String relatedReference,
      String description,
      Instant transactionTime) {
    validateCurrency(currency);
    if (organizationReference == null
        || organizationReference.isBlank()
        || transactionReference == null
        || !TRANSACTION_REFERENCE.matcher(transactionReference).matches()
        || transactionType == null
        || activityType == null
        || amount == null
        || amount.signum() <= 0
        || relatedReferenceType == null
        || relatedReferenceType.isBlank()
        || relatedReferenceType.length() > 40
        || relatedReference == null
        || relatedReference.isBlank()
        || relatedReference.length() > 80
        || description == null
        || description.isBlank()
        || description.length() > 255
        || transactionTime == null
        || transactionTime.isAfter(clock.instant())) {
      throw BorrowerException.invalid("Invalid posted cash transaction");
    }
    try {
      amount.setScale(2, RoundingMode.UNNECESSARY);
      if (amount.precision() > 20) {
        throw BorrowerException.invalid("Cash transaction exceeds supported precision");
      }
    } catch (ArithmeticException exception) {
      throw BorrowerException.invalid("Cash transaction must have at most two decimal places");
    }
  }

  private boolean sameTransaction(
      BorrowerCashTransaction existing,
      BorrowerCashAccount account,
      BorrowerCashTransactionType transactionType,
      BorrowerCashActivityType activityType,
      BigDecimal amount,
      String relatedReferenceType,
      String relatedReference,
      String description,
      Instant transactionTime) {
    return Objects.equals(existing.getAccount().getAccountId(), account.getAccountId())
        && existing.getTransactionType() == transactionType
        && Objects.equals(existing.getActivityType(), activityType.name())
        && existing.getAmount().compareTo(amount) == 0
        && Objects.equals(existing.getRelatedReferenceType(), relatedReferenceType)
        && Objects.equals(existing.getRelatedReference(), relatedReference)
        && Objects.equals(existing.getDescription(), description)
        && Objects.equals(existing.getTransactionTime(), transactionTime);
  }

  private void validateCurrency(String currency) {
    if (currency == null || !CURRENCY.matcher(currency).matches()) {
      throw BorrowerException.invalid("Currency must be a three-letter uppercase code");
    }
  }

  private BorrowerCashAccountResponse accountResponse(BorrowerCashAccount account) {
    return new BorrowerCashAccountResponse(
        account.getAccountId(),
        account.getBorrowerOrganizationReference(),
        account.getCurrency(),
        account.getCashBalance(),
        account.getStatus(),
        account.getCreatedAt(),
        account.getUpdatedAt());
  }

  private BorrowerCashTransactionResponse transactionResponse(
      BorrowerCashTransaction transaction, String currency) {
    return new BorrowerCashTransactionResponse(
        transaction.getTransactionReference(),
        currency,
        transaction.getTransactionType(),
        transaction.getActivityType(),
        transaction.getAmount(),
        transaction.getRelatedReferenceType(),
        transaction.getRelatedReference(),
        transaction.getDescription(),
        transaction.getTransactionTime(),
        transaction.getRecordedAt());
  }
}
