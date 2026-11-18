package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.BorrowerCashAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowerCashAccountRepository
    extends JpaRepository<BorrowerCashAccount, Long> {
  List<BorrowerCashAccount> findByBorrowerOrganizationReferenceOrderByCurrencyAsc(
      String borrowerOrganizationReference);

  Optional<BorrowerCashAccount> findByBorrowerOrganizationReferenceAndCurrency(
      String borrowerOrganizationReference, String currency);

  @Modifying
  @Query(
      value =
          "INSERT INTO borrower_cash_account "
              + "(borrower_organization_reference, currency, cash_balance, status, version) "
              + "VALUES (:organizationReference, :currency, 0, 'ACTIVE', 0) "
              + "ON DUPLICATE KEY UPDATE account_id = account_id",
      nativeQuery = true)
  int createIfAbsent(
      @Param("organizationReference") String organizationReference,
      @Param("currency") String currency);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select account from BorrowerCashAccount account "
          + "where account.borrowerOrganizationReference = :organizationReference "
          + "and account.currency = :currency")
  Optional<BorrowerCashAccount> findLockedByOrganizationAndCurrency(
      @Param("organizationReference") String organizationReference,
      @Param("currency") String currency);
}
