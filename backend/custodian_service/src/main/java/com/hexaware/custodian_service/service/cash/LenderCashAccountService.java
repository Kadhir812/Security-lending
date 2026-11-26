package com.hexaware.custodian_service.service.cash;

import com.hexaware.custodian_service.dto.request.LenderCashMovementRequest;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LenderCashAccountRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LenderCashAccountService {
  private final LendingSetupAccess access;
  private final SetupAccess identity;
  private final LenderCashLedgerService ledger;
  private final LenderCashAccountRepository accounts;

  @Transactional(readOnly = true)
  public Map<String, Object> account(Authentication authentication, String lenderReference, String currency) {
    var lender = access.requireLender(authentication, lenderReference, false);
    String normalizedCurrency = SetupValidation.currency(currency);
    Map<String, Object> result = new LinkedHashMap<>(ledger.balance(lender.getOrganizationId(), normalizedCurrency));
    accounts.findByOrganizationIdAndCurrency(lender.getOrganizationId(), normalizedCurrency)
        .ifPresent(account -> {
          result.put("accountReference", account.getAccountReference());
          result.put("status", account.getStatus());
          result.put("updatedAt", account.getUpdatedAt());
        });
    result.put("lenderOrganizationReference", lender.getOrganizationReference());
    return result;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> history(Authentication authentication, String lenderReference, String currency) {
    var lender = access.requireLender(authentication, lenderReference, false);
    return ledger.history(lender.getOrganizationId(), SetupValidation.currency(currency)).stream()
        .map(transaction -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("transactionReference", transaction.getTransactionReference());
          row.put("transactionType", transaction.getTransactionType());
          row.put("sourceType", transaction.getSourceType());
          row.put("sourceReference", transaction.getSourceReference());
          row.put("cashDelta", transaction.getCashDelta());
          row.put("restrictedDelta", transaction.getRestrictedDelta());
          row.put("cashBalanceAfter", transaction.getCashBalanceAfter());
          row.put("restrictedBalanceAfter", transaction.getRestrictedBalanceAfter());
          row.put("description", transaction.getDescription());
          row.put("createdAt", transaction.getCreatedAt());
          return row;
        }).toList();
  }

  @Transactional
  public Map<String, Object> move(Authentication authentication, String lenderReference, String currency,
      String idempotencyKey, LenderCashMovementRequest request, boolean deposit) {
    var lender = access.requireLender(authentication, lenderReference, true);
    var key = idempotencyKey == null ? "" : idempotencyKey.strip();
    if (key.isEmpty() || key.length() > 100) throw SetupException.invalid("Idempotency-Key is required");
    ledger.recordExternalMovement(lender.getOrganizationId(), SetupValidation.currency(currency),
        request.amount(), deposit, request.externalReference(), key, request.description(),
        identity.user(authentication).getUserId());
    return account(authentication, lenderReference, currency);
  }
}
