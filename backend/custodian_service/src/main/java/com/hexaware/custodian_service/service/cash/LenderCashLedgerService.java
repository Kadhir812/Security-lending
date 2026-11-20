package com.hexaware.custodian_service.service.cash;

import com.hexaware.custodian_service.entity.LenderCashAccount;
import com.hexaware.custodian_service.entity.LenderCashTransaction;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LenderCashAccountRepository;
import com.hexaware.custodian_service.repository.LenderCashTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LenderCashLedgerService {
  private final LenderCashAccountRepository accounts;
  private final LenderCashTransactionRepository transactions;
  private final Clock clock;

  @Transactional
  public void receiveCollateral(Long lenderOrganizationId, String currency,
      BigDecimal amount, String instructionReference) {
    LenderCashAccount account = lockAccount(lenderOrganizationId, currency);
    post(account, "COLLATERAL_RECEIVED", "SETTLEMENT", instructionReference,
        "COLLATERAL-RECEIVED:" + instructionReference, amount, amount,
        "Confirmed collateral received; held as restricted cash");
  }

  @Transactional
  public void releaseCollateral(Long lenderOrganizationId, String currency,
      BigDecimal amount, String instructionReference) {
    LenderCashAccount account = lockAccount(lenderOrganizationId, currency);
    post(account, "COLLATERAL_RELEASED", "SETTLEMENT", instructionReference,
        "COLLATERAL-RELEASED:" + instructionReference, amount.negate(), amount.negate(),
        "Confirmed collateral payout");
  }

  @Transactional
  public void receiveFeePayment(Long lenderOrganizationId, String currency,
      BigDecimal amount, String paymentReference) {
    LenderCashAccount account = lockAccount(lenderOrganizationId, currency);
    post(account, "LENDING_FEE_RECEIVED", "FEE_PAYMENT", paymentReference,
        "FEE-RECEIVED:" + paymentReference, amount, BigDecimal.ZERO.setScale(2),
        "Confirmed lending fee payment received");
  }

  @Transactional(readOnly = true)
  public Map<String, Object> balance(Long lenderOrganizationId, String currency) {
    LenderCashAccount account = accounts.findByOrganizationIdAndCurrency(lenderOrganizationId, currency)
        .orElse(null);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("currency", currency);
    result.put("cashBalance", account == null ? BigDecimal.ZERO.setScale(2) : account.getCashBalance());
    result.put("restrictedBalance", account == null
        ? BigDecimal.ZERO.setScale(2) : account.getRestrictedBalance());
    result.put("availableBalance", account == null
        ? BigDecimal.ZERO.setScale(2)
        : account.getCashBalance().subtract(account.getRestrictedBalance()));
    return result;
  }

  private LenderCashAccount lockAccount(Long organizationId, String currency) {
    if (organizationId == null || currency == null || !currency.matches("[A-Z]{3}")) {
      throw SetupException.conflict("Lender cash account identity is invalid");
    }
    accounts.ensureAccount("LCASH-" + shortId(), organizationId, currency);
    return accounts.findLocked(organizationId, currency)
        .orElseThrow(() -> SetupException.conflict("Lender cash account could not be created"));
  }

  private void post(LenderCashAccount account, String type, String sourceType,
      String sourceReference, String key, BigDecimal cashDelta, BigDecimal restrictedDelta,
      String description) {
    var existing = transactions.findByCashAccountIdAndIdempotencyKey(
        account.getCashAccountId(), key).orElse(null);
    if (existing != null) {
      if (!type.equals(existing.getTransactionType())
          || !sourceReference.equals(existing.getSourceReference())
          || existing.getCashDelta().compareTo(cashDelta) != 0
          || existing.getRestrictedDelta().compareTo(restrictedDelta) != 0) {
        throw SetupException.conflict("Lender cash movement idempotency key conflict");
      }
      return;
    }
    BigDecimal nextCash = account.getCashBalance().add(cashDelta).setScale(2, RoundingMode.UNNECESSARY);
    BigDecimal nextRestricted = account.getRestrictedBalance().add(restrictedDelta)
        .setScale(2, RoundingMode.UNNECESSARY);
    if (nextCash.signum() < 0 || nextRestricted.signum() < 0
        || nextRestricted.compareTo(nextCash) > 0) {
      throw SetupException.conflict("Lender collateral release exceeds restricted cash");
    }
    account.setCashBalance(nextCash);
    account.setRestrictedBalance(nextRestricted);
    account.setUpdatedAt(clock.instant());
    accounts.save(account);
    transactions.save(LenderCashTransaction.builder()
        .transactionReference("LCASH-TXN-" + shortId())
        .cashAccountId(account.getCashAccountId())
        .transactionType(type)
        .sourceType(sourceType)
        .sourceReference(sourceReference)
        .idempotencyKey(key)
        .cashDelta(cashDelta.setScale(2, RoundingMode.UNNECESSARY))
        .restrictedDelta(restrictedDelta.setScale(2, RoundingMode.UNNECESSARY))
        .cashBalanceAfter(nextCash)
        .restrictedBalanceAfter(nextRestricted)
        .description(description)
        .createdAt(clock.instant())
        .build());
  }

  private String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        .toUpperCase(java.util.Locale.ROOT);
  }
}
