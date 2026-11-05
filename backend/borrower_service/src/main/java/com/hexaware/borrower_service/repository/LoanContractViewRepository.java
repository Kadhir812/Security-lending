package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.LoanContractView;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoanContractViewRepository extends JpaRepository<LoanContractView, Long> {
  Optional<LoanContractView> findByRequestReference(String requestReference);
  Optional<LoanContractView> findByTransactionReference(String transactionReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select contractView from LoanContractView contractView "
      + "where contractView.transactionReference = :transactionReference")
  Optional<LoanContractView> findLockedByTransactionReference(String transactionReference);
}
