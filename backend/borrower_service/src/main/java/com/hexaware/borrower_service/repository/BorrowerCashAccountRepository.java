package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.BorrowerCashAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowerCashAccountRepository extends JpaRepository<BorrowerCashAccount, Long> {
  @Modifying
  @Query(value = "INSERT INTO borrower_cash_account "
      + "(account_reference, borrower_organization_reference, currency, cash_balance, reserved_balance, status, created_at, updated_at) "
      + "VALUES (:accountReference, :organizationReference, :currency, 0, 0, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) "
      + "ON DUPLICATE KEY UPDATE cash_account_id = cash_account_id", nativeQuery = true)
  void ensureAccount(@Param("accountReference") String accountReference,
      @Param("organizationReference") String organizationReference,
      @Param("currency") String currency);

  Optional<BorrowerCashAccount> findByBorrowerOrganizationReferenceAndCurrency(
      String organizationReference, String currency);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from BorrowerCashAccount account "
      + "where account.borrowerOrganizationReference = :organizationReference "
      + "and account.currency = :currency")
  Optional<BorrowerCashAccount> findLocked(
      @Param("organizationReference") String organizationReference,
      @Param("currency") String currency);
}
