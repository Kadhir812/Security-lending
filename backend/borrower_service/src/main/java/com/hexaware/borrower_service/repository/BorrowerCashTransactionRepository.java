package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.BorrowerCashTransaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerCashTransactionRepository
    extends JpaRepository<BorrowerCashTransaction, Long> {
  Optional<BorrowerCashTransaction> findByTransactionReference(String transactionReference);

  Page<BorrowerCashTransaction> findByAccount_AccountIdOrderByTransactionTimeDesc(
      Long accountId, Pageable pageable);
}
