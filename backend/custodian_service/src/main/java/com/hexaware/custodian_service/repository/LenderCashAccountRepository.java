package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LenderCashAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LenderCashAccountRepository extends JpaRepository<LenderCashAccount, Long> {
  @Modifying
  @Query(value = "INSERT INTO lender_cash_account "
      + "(account_reference, organization_id, currency, cash_balance, restricted_balance, status, created_at, updated_at) "
      + "VALUES (:accountReference, :organizationId, :currency, 0, 0, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) "
      + "ON DUPLICATE KEY UPDATE cash_account_id = cash_account_id", nativeQuery = true)
  void ensureAccount(@Param("accountReference") String accountReference,
      @Param("organizationId") Long organizationId, @Param("currency") String currency);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from LenderCashAccount account "
      + "where account.organizationId = :organizationId and account.currency = :currency")
  Optional<LenderCashAccount> findLocked(
      @Param("organizationId") Long organizationId, @Param("currency") String currency);

  Optional<LenderCashAccount> findByOrganizationIdAndCurrency(Long organizationId, String currency);
}
