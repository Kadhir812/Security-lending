package com.hexaware.borrower_service.service.cash;

import com.hexaware.borrower_service.dto.request.CashMovementRequest;
import com.hexaware.borrower_service.dto.request.TradeExecutionRequest;
import com.hexaware.borrower_service.entity.BorrowerCashAccount;
import com.hexaware.borrower_service.entity.BorrowerCashTransaction;
import com.hexaware.borrower_service.entity.BorrowerTradeActivity;
import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.enums.BorrowerTradeType;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.BorrowerCashAccountRepository;
import com.hexaware.borrower_service.repository.BorrowerCashTransactionRepository;
import com.hexaware.borrower_service.repository.BorrowerTradeActivityRepository;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
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
public class BorrowerCashAccountService {
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private final BorrowerCashAccountRepository accounts;
  private final BorrowerCashTransactionRepository transactions;
  private final BorrowerTradeActivityRepository trades;
  private final LoanContractViewRepository contractViews;
  private final LoanRequestRepository requests;
  private final Clock clock;

  @Transactional
  public Map<String, Object> account(Authentication authentication, String currency) {
    LocalUserAuthentication user = trader(authentication);
    return accountView(lockAccount(user.getOrganizationReference(), currency));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> transactionHistory(
      Authentication authentication, String currency) {
    LocalUserAuthentication user = trader(authentication);
    BorrowerCashAccount account = accounts
        .findByBorrowerOrganizationReferenceAndCurrency(user.getOrganizationReference(), currency)
        .orElse(null);
    if (account == null) return List.of();
    return transactions
        .findTop100ByCashAccountIdOrderByCreatedAtDescCashTransactionIdDesc(account.getCashAccountId())
        .stream().map(this::transactionView).toList();
  }

  @Transactional
  public Map<String, Object> deposit(
      Authentication authentication, String currency, String idempotencyKey,
      CashMovementRequest request) {
    LocalUserAuthentication user = trader(authentication);
    requireMoney(request.amount());
    BorrowerCashAccount account = lockAccount(user.getOrganizationReference(), currency);
    BorrowerCashTransaction transaction = post(account, "DEPOSIT", "MANUAL",
        request.sourceReference(), key(idempotencyKey), request.amount(), ZERO,
        request.sourceReference(), user.getUserId());
    return transactionView(transaction);
  }

  @Transactional
  public Map<String, Object> withdraw(
      Authentication authentication, String currency, String idempotencyKey,
      CashMovementRequest request) {
    LocalUserAuthentication user = trader(authentication);
    requireMoney(request.amount());
    BorrowerCashAccount account = lockAccount(user.getOrganizationReference(), currency);
    BorrowerCashTransaction transaction = post(account, "WITHDRAWAL", "MANUAL",
        request.sourceReference(), key(idempotencyKey), request.amount().negate(), ZERO,
        request.sourceReference(), user.getUserId());
    return transactionView(transaction);
  }

  @Transactional
  public Map<String, Object> executeTrade(
      Authentication authentication, TradeExecutionRequest request) {
    LocalUserAuthentication user = trader(authentication);
    LoanContractView view = contractViews.findLockedByTransactionReference(
            request.transactionReference())
        .orElseThrow(() -> BorrowerException.missing("Loan contract not found"));
    LoanRequest loanRequest = requests.findByRequestReferenceAndBorrowerOrganizationReference(
            view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Loan contract not found"));
    Map<String, Object> snapshot = view.getContractSnapshot();
    if (!"ON_LOAN".equals(snapshot.get("contractStatus"))) {
      throw BorrowerException.conflict("Trading requires confirmed securities delivery");
    }
    String currency = String.valueOf(snapshot.get("settlementCurrency"));
    if (!currency.equals(loanRequest.getSettlementCurrency())) {
      throw BorrowerException.conflict("Contract currency projection is invalid");
    }
    BorrowerTradeActivity existing = trades.findByTradeReference(request.tradeReference())
        .orElse(null);
    if (existing != null) {
      if (!existing.getTransactionReference().equals(request.transactionReference())
          || existing.getTradeType() != request.tradeType()
          || existing.getQuantity().longValue() != request.quantity().longValue()
          || existing.getPrice().compareTo(request.price()) != 0) {
        throw BorrowerException.conflict("Trade reference was reused with different inputs");
      }
      return tradeView(existing, currency);
    }

    List<BorrowerTradeActivity> history =
        trades.findByTransactionReferenceOrderByTradeIdAsc(request.transactionReference());
    Deque<ShortLot> openLots = openShortLots(history);
    long openQuantity = openLots.stream().mapToLong(ShortLot::quantity).sum();
    BigDecimal notional = request.price().multiply(BigDecimal.valueOf(request.quantity()))
        .setScale(2, RoundingMode.HALF_UP);
    BigDecimal realized = ZERO;
    BigDecimal cashDelta;
    if (request.tradeType() == BorrowerTradeType.SELL_SHORT) {
      long outstanding = longValue(snapshot.get("outstandingQuantity"));
      if (outstanding <= 0 || Math.addExact(openQuantity, request.quantity()) > outstanding) {
        throw BorrowerException.conflict("Short sale exceeds confirmed, unreturned borrowed shares");
      }
      cashDelta = notional;
    } else {
      if (request.quantity() > openQuantity) {
        throw BorrowerException.conflict("Buy-to-cover exceeds the open short position");
      }
      realized = realizeCover(openLots, request.quantity(), request.price());
      cashDelta = notional.negate();
    }

    BorrowerCashAccount account = lockAccount(user.getOrganizationReference(), currency);
    String cashType = request.tradeType() == BorrowerTradeType.SELL_SHORT
        ? "SHORT_SALE_PROCEEDS" : "BUY_TO_COVER";
    post(account, cashType, "TRADE", request.tradeReference(),
        request.tradeReference(), cashDelta, ZERO,
        request.tradeType() + " " + request.quantity() + " @ " + request.price(),
        user.getUserId());
    BorrowerTradeActivity trade = trades.save(BorrowerTradeActivity.builder()
        .tradeReference(request.tradeReference())
        .transactionReference(request.transactionReference())
        .tradeType(request.tradeType())
        .quantity(request.quantity())
        .price(request.price())
        .realizedProfitLoss(request.tradeType() == BorrowerTradeType.BUY_TO_COVER
            ? realized.setScale(2, RoundingMode.HALF_UP) : null)
        .tradeTime(clock.instant())
        .createdBy(user.getUserId())
        .build());
    return tradeView(trade, currency);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> tradeHistory(
      Authentication authentication, String transactionReference) {
    LocalUserAuthentication user = trader(authentication);
    LoanContractView view = contractViews.findByTransactionReference(transactionReference)
        .orElseThrow(() -> BorrowerException.missing("Loan contract not found"));
    LoanRequest loanRequest = requests.findByRequestReferenceAndBorrowerOrganizationReference(
            view.getRequestReference(), user.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Loan contract not found"));
    String currency = loanRequest.getSettlementCurrency();
    return trades.findByTransactionReferenceOrderByTradeIdAsc(transactionReference)
        .stream().map(row -> tradeView(row, currency)).toList();
  }

  @Transactional
  public void reserveCollateral(String organizationReference, String currency,
      BigDecimal amount, String instructionReference, Long userId) {
    BorrowerCashAccount account = lockAccount(organizationReference, currency);
    post(account, "COLLATERAL_CASH_RESERVED", "SETTLEMENT", instructionReference,
        "COLLATERAL-RESERVE:" + instructionReference, ZERO, amount,
        "Reserve cash for collateral instruction", userId);
  }

  @Transactional
  public void settleCollateral(String organizationReference, String currency,
      BigDecimal amount, String instructionReference, boolean settled) {
    BorrowerCashAccount account = lockAccount(organizationReference, currency);
    String reserveKey = "COLLATERAL-RESERVE:" + instructionReference;
    BorrowerCashTransaction reservation = transactions.findByCashAccountIdAndIdempotencyKey(
        account.getCashAccountId(), reserveKey).orElse(null);
    boolean reserved = reservation != null;
    if (reserved && reservation.getReservedDelta().compareTo(amount) != 0) {
      throw BorrowerException.conflict("Collateral settlement amount differs from reserved cash");
    }
    if (!settled && !reserved) return;
    post(account, settled ? "COLLATERAL_POSTED" : "CASH_RESERVATION_RELEASED",
        "SETTLEMENT", instructionReference, "COLLATERAL-SETTLE:" + instructionReference,
        settled ? amount.negate() : ZERO,
        reserved ? amount.negate() : ZERO,
        settled ? "Settled cash collateral" : "Released failed collateral reservation", null);
  }

  @Transactional
  public void receiveCollateralRelease(String organizationReference, String currency,
      BigDecimal amount, String instructionReference) {
    BorrowerCashAccount account = lockAccount(organizationReference, currency);
    post(account, "COLLATERAL_RELEASED", "SETTLEMENT", instructionReference,
        "COLLATERAL-RELEASE:" + instructionReference, amount, ZERO,
        "Cash collateral returned after confirmed release", null);
  }

  @Transactional
  public void reserveFeePayment(String organizationReference, String currency,
      BigDecimal amount, String paymentReference, Long userId) {
    BorrowerCashAccount account = lockAccount(organizationReference, currency);
    post(account, "FEE_CASH_RESERVED", "FEE_PAYMENT", paymentReference,
        "FEE-RESERVE:" + paymentReference, ZERO, amount,
        "Reserve cash for submitted lending fee payment", userId);
  }

  @Transactional
  public void settleFeePayment(String organizationReference, String currency,
      BigDecimal amount, String paymentReference, boolean paid) {
    BorrowerCashAccount account = lockAccount(organizationReference, currency);
    String reserveKey = "FEE-RESERVE:" + paymentReference;
    BorrowerCashTransaction reservation = transactions.findByCashAccountIdAndIdempotencyKey(
        account.getCashAccountId(), reserveKey).orElse(null);
    boolean reserved = reservation != null;
    if (reserved && reservation.getReservedDelta().compareTo(amount) != 0) {
      throw BorrowerException.conflict("Fee settlement amount differs from reserved cash");
    }
    if (!paid && !reserved) return;
    post(account, paid ? "LENDING_FEE_PAID" : "CASH_RESERVATION_RELEASED",
        paid ? "FEE_PAYMENT" : "REVERSAL", paymentReference,
        "FEE-SETTLE:" + paymentReference,
        paid ? amount.negate() : ZERO, reserved ? amount.negate() : ZERO,
        paid ? "Confirmed lending fee payment" : "Released failed fee payment reservation", null);
  }

  private BorrowerCashAccount lockAccount(String organizationReference, String currency) {
    if (currency == null || !currency.matches("[A-Z]{3}")) {
      throw BorrowerException.invalid("Currency must be a three-letter uppercase code");
    }
    accounts.ensureAccount("BCA-" + UUID.randomUUID(), organizationReference, currency);
    BorrowerCashAccount account = accounts.findLocked(organizationReference, currency)
        .orElseThrow(() -> BorrowerException.conflict("Cash account could not be created"));
    if (!"ACTIVE".equals(account.getStatus())) {
      throw BorrowerException.conflict("Cash account is not active");
    }
    return account;
  }

  private BorrowerCashTransaction post(BorrowerCashAccount account, String type,
      String sourceType, String sourceReference, String idempotencyKey,
      BigDecimal cashDelta, BigDecimal reservedDelta, String description, Long userId) {
    var existing = transactions.findByCashAccountIdAndIdempotencyKey(
        account.getCashAccountId(), idempotencyKey).orElse(null);
    if (existing != null) {
      if (!type.equals(existing.getTransactionType())
          || !sourceReference.equals(existing.getSourceReference())
          || existing.getCashDelta().compareTo(cashDelta) != 0
          || existing.getReservedDelta().compareTo(reservedDelta) != 0) {
        throw BorrowerException.conflict("Cash idempotency key was reused with different inputs");
      }
      return existing;
    }
    BigDecimal nextCash = account.getCashBalance().add(cashDelta).setScale(2, RoundingMode.UNNECESSARY);
    BigDecimal nextReserved = account.getReservedBalance().add(reservedDelta)
        .setScale(2, RoundingMode.UNNECESSARY);
    if (nextCash.signum() < 0 || nextReserved.signum() < 0 || nextReserved.compareTo(nextCash) > 0) {
      throw BorrowerException.conflict("Cash movement exceeds available balance");
    }
    account.setCashBalance(nextCash);
    account.setReservedBalance(nextReserved);
    account.setUpdatedAt(clock.instant());
    accounts.save(account);
    return transactions.save(BorrowerCashTransaction.builder()
        .transactionReference("BCT-" + UUID.randomUUID())
        .cashAccountId(account.getCashAccountId())
        .transactionType(type)
        .sourceType(sourceType)
        .sourceReference(sourceReference)
        .idempotencyKey(idempotencyKey)
        .cashDelta(cashDelta.setScale(2, RoundingMode.UNNECESSARY))
        .reservedDelta(reservedDelta.setScale(2, RoundingMode.UNNECESSARY))
        .cashBalanceAfter(nextCash)
        .reservedBalanceAfter(nextReserved)
        .description(description)
        .createdByUserId(userId)
        .createdAt(clock.instant())
        .build());
  }

  private Deque<ShortLot> openShortLots(List<BorrowerTradeActivity> history) {
    Deque<ShortLot> lots = new ArrayDeque<>();
    for (BorrowerTradeActivity trade : history) {
      if (trade.getTradeType() == BorrowerTradeType.SELL_SHORT) {
        lots.addLast(new ShortLot(trade.getQuantity(), trade.getPrice()));
      } else {
        realizeCover(lots, trade.getQuantity(), trade.getPrice());
      }
    }
    return lots;
  }

  private BigDecimal realizeCover(Deque<ShortLot> lots, long quantity, BigDecimal coverPrice) {
    long remaining = quantity;
    BigDecimal realized = BigDecimal.ZERO;
    while (remaining > 0 && !lots.isEmpty()) {
      ShortLot lot = lots.removeFirst();
      long matched = Math.min(remaining, lot.quantity());
      realized = realized.add(lot.sellPrice().subtract(coverPrice)
          .multiply(BigDecimal.valueOf(matched)));
      if (lot.quantity() > matched) {
        lots.addFirst(new ShortLot(lot.quantity() - matched, lot.sellPrice()));
      }
      remaining -= matched;
    }
    if (remaining > 0) throw BorrowerException.conflict("Buy-to-cover exceeds the open short position");
    return realized;
  }

  private Map<String, Object> accountView(BorrowerCashAccount account) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("accountReference", account.getAccountReference());
    result.put("organizationReference", account.getBorrowerOrganizationReference());
    result.put("currency", account.getCurrency());
    result.put("cashBalance", account.getCashBalance());
    result.put("reservedBalance", account.getReservedBalance());
    result.put("availableBalance", account.getCashBalance().subtract(account.getReservedBalance()));
    result.put("status", account.getStatus());
    return result;
  }

  private Map<String, Object> transactionView(BorrowerCashTransaction transaction) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("transactionReference", transaction.getTransactionReference());
    result.put("transactionType", transaction.getTransactionType());
    result.put("sourceType", transaction.getSourceType());
    result.put("sourceReference", transaction.getSourceReference());
    result.put("cashDelta", transaction.getCashDelta());
    result.put("reservedDelta", transaction.getReservedDelta());
    result.put("cashBalanceAfter", transaction.getCashBalanceAfter());
    result.put("reservedBalanceAfter", transaction.getReservedBalanceAfter());
    result.put("availableBalanceAfter",
        transaction.getCashBalanceAfter().subtract(transaction.getReservedBalanceAfter()));
    result.put("description", transaction.getDescription());
    result.put("createdAt", transaction.getCreatedAt());
    return result;
  }

  private Map<String, Object> tradeView(BorrowerTradeActivity trade, String currency) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tradeReference", trade.getTradeReference());
    result.put("transactionReference", trade.getTransactionReference());
    result.put("tradeType", trade.getTradeType());
    result.put("quantity", trade.getQuantity());
    result.put("price", trade.getPrice());
    result.put("currency", currency);
    result.put("notional", trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity()))
        .setScale(2, RoundingMode.HALF_UP));
    result.put("realizedProfitLoss", trade.getRealizedProfitLoss());
    result.put("tradeTime", trade.getTradeTime());
    return result;
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

  private String key(String value) {
    if (value == null || !value.matches("[A-Za-z0-9._:-]{1,100}")) {
      throw BorrowerException.invalid("A valid Idempotency-Key is required");
    }
    return value;
  }

  private void requireMoney(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
      throw BorrowerException.invalid("Cash amount must be positive and no greater than two decimals");
    }
  }

  private long longValue(Object value) {
    try {
      return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    } catch (RuntimeException exception) {
      throw BorrowerException.conflict("Contract delivery quantity is invalid");
    }
  }

  private record ShortLot(long quantity, BigDecimal sellPrice) {}
}
