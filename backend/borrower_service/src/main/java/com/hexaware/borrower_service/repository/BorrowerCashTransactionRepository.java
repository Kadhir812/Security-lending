package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.BorrowerCashTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerCashTransactionRepository
    extends JpaRepository<BorrowerCashTransaction, Long> {
  Optional<BorrowerCashTransaction> findByCashAccountIdAndIdempotencyKey(
      Long cashAccountId, String idempotencyKey);

  List<BorrowerCashTransaction> findTop100ByCashAccountIdOrderByCreatedAtDescCashTransactionIdDesc(
      Long cashAccountId);
}
