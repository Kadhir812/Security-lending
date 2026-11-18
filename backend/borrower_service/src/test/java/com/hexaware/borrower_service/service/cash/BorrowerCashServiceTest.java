package com.hexaware.borrower_service.service.cash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.dto.request.BorrowerExternalCashMovementRequest;
import com.hexaware.borrower_service.entity.BorrowerCashAccount;
import com.hexaware.borrower_service.entity.BorrowerCashTransaction;
import com.hexaware.borrower_service.enums.BorrowerCashAccountStatus;
import com.hexaware.borrower_service.enums.BorrowerCashActivityType;
import com.hexaware.borrower_service.enums.BorrowerCashTransactionType;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.BorrowerCashAccountRepository;
import com.hexaware.borrower_service.repository.BorrowerCashTransactionRepository;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class BorrowerCashServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
  private static final String ORGANIZATION = "BORROWER-001";
  private static final String CURRENCY = "USD";
  private static final String TRANSACTION_REFERENCE = "CASH:TRADE:TRADE-001";

  @Mock private BorrowerCashAccountRepository accounts;
  @Mock private BorrowerCashTransactionRepository transactions;
  @Mock private LoanRequestSubmissionValidator validator;

  private BorrowerCashService service;
  private BorrowerCashAccount account;

  @BeforeEach
  void setUp() {
    service =
        new BorrowerCashService(
            accounts, transactions, validator, Clock.fixed(NOW, ZoneOffset.UTC));
    account =
        BorrowerCashAccount.builder()
            .accountId(1L)
            .borrowerOrganizationReference(ORGANIZATION)
            .currency(CURRENCY)
            .cashBalance(new BigDecimal("0.00"))
            .status(BorrowerCashAccountStatus.ACTIVE)
            .version(0L)
            .build();
    when(accounts.findLockedByOrganizationAndCurrency(ORGANIZATION, CURRENCY))
        .thenReturn(Optional.of(account));
  }

  @Test
  void appliesTradeCreditsAndDebitsToTheAccountBalance() {
    when(transactions.findByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.empty());
    when(transactions.findByTransactionReference("CASH:COVER:TRADE-002"))
        .thenReturn(Optional.empty());
    when(transactions.save(any(BorrowerCashTransaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recordTransaction(
        ORGANIZATION,
        CURRENCY,
        TRANSACTION_REFERENCE,
        BorrowerCashTransactionType.CREDIT,
        BorrowerCashActivityType.SHORT_SALE_PROCEEDS,
        new BigDecimal("5000.00"),
        "TRADE",
        "TRADE-001",
        "Short-sale proceeds",
        NOW.minusSeconds(30));
    service.recordTransaction(
        ORGANIZATION,
        CURRENCY,
        "CASH:COVER:TRADE-002",
        BorrowerCashTransactionType.DEBIT,
        BorrowerCashActivityType.BUY_TO_COVER_PAYMENT,
        new BigDecimal("4500.00"),
        "TRADE",
        "TRADE-002",
        "Buy-to-cover payment",
        NOW.minusSeconds(10));

    assertThat(account.getCashBalance()).isEqualByComparingTo("500.00");
    verify(transactions, org.mockito.Mockito.times(2)).save(any(BorrowerCashTransaction.class));
  }

  @Test
  void duplicateTransactionReferenceDoesNotChangeTheBalanceAgain() {
    account.setCashBalance(new BigDecimal("5000.00"));
    BorrowerCashTransaction existing =
        BorrowerCashTransaction.builder()
            .account(account)
            .transactionReference(TRANSACTION_REFERENCE)
            .transactionType(BorrowerCashTransactionType.CREDIT)
            .activityType(BorrowerCashActivityType.SHORT_SALE_PROCEEDS.name())
            .amount(new BigDecimal("5000.00"))
            .relatedReferenceType("TRADE")
            .relatedReference("TRADE-001")
            .description("Short-sale proceeds")
            .transactionTime(NOW.minusSeconds(30))
            .build();
    when(transactions.findByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(existing));

    service.recordTransaction(
        ORGANIZATION,
        CURRENCY,
        TRANSACTION_REFERENCE,
        BorrowerCashTransactionType.CREDIT,
        BorrowerCashActivityType.SHORT_SALE_PROCEEDS,
        new BigDecimal("5000.00"),
        "TRADE",
        "TRADE-001",
        "Short-sale proceeds",
        NOW.minusSeconds(30));

    assertThat(account.getCashBalance()).isEqualByComparingTo("5000.00");
    verify(accounts, never()).save(any(BorrowerCashAccount.class));
    verify(transactions, never()).save(any(BorrowerCashTransaction.class));
  }

  @Test
  void rejectsDebitsGreaterThanTheRecordedCashBalance() {
    account.setCashBalance(new BigDecimal("50.00"));
    when(transactions.findByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.recordTransaction(
                    ORGANIZATION,
                    CURRENCY,
                    TRANSACTION_REFERENCE,
                    BorrowerCashTransactionType.DEBIT,
                    BorrowerCashActivityType.BUY_TO_COVER_PAYMENT,
                    new BigDecimal("60.00"),
                    "TRADE",
                    "TRADE-001",
                    "Buy-to-cover payment",
                    NOW.minusSeconds(10)))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("exceeds the account balance");

    assertThat(account.getCashBalance()).isEqualByComparingTo("50.00");
    verify(transactions, never()).save(any(BorrowerCashTransaction.class));
  }

  @Test
  void recordsBorrowerEnteredExternalFundingAndAuditsTheUser() {
    LocalUserAuthentication authentication = authentication();
    when(validator.requireBorrower(authentication)).thenReturn(authentication);
    AtomicReference<BorrowerCashTransaction> savedTransaction = new AtomicReference<>();
    when(transactions.findByTransactionReference("CASH:EXTERNAL:DEPOSIT-001"))
        .thenAnswer(invocation -> Optional.ofNullable(savedTransaction.get()));
    when(transactions.save(any(BorrowerCashTransaction.class)))
        .thenAnswer(
            invocation -> {
              BorrowerCashTransaction transaction = invocation.getArgument(0);
              savedTransaction.set(transaction);
              return transaction;
            });
    BorrowerExternalCashMovementRequest request =
        new BorrowerExternalCashMovementRequest(
            BorrowerCashTransactionType.CREDIT,
            new BigDecimal("2500.00"),
            "BANK-TRANSFER-001",
            "Opening cash deposit",
            NOW.minusSeconds(60));

    var response =
        service.recordExternalMovement(authentication, CURRENCY, "DEPOSIT-001", request);
    var replay =
        service.recordExternalMovement(authentication, CURRENCY, "DEPOSIT-001", request);

    assertThat(response.transactionReference()).isEqualTo("CASH:EXTERNAL:DEPOSIT-001");
    assertThat(replay.transactionReference()).isEqualTo(response.transactionReference());
    assertThat(account.getCashBalance()).isEqualByComparingTo("2500.00");
    ArgumentCaptor<BorrowerCashTransaction> transaction =
        ArgumentCaptor.forClass(BorrowerCashTransaction.class);
    verify(transactions).save(transaction.capture());
    assertThat(transaction.getValue().getRecordedByUserId()).isEqualTo(42L);
    assertThat(transaction.getValue().getActivityType()).isEqualTo("EXTERNAL_CASH_MOVEMENT");
  }

  private LocalUserAuthentication authentication() {
    Jwt token =
        Jwt.withTokenValue("cash-test-token")
            .header("alg", "none")
            .subject("borrower-user")
            .issuedAt(NOW.minusSeconds(60))
            .expiresAt(NOW.plusSeconds(3600))
            .build();
    return new LocalUserAuthentication(
        token, List.of(new SimpleGrantedAuthority("ROLE_TRADER")), 42L, ORGANIZATION);
  }
}
