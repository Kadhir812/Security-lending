package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LenderCashTransaction;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LenderCashTransactionRepository
    extends JpaRepository<LenderCashTransaction, Long> {
  Optional<LenderCashTransaction> findByCashAccountIdAndIdempotencyKey(
      Long cashAccountId, String idempotencyKey);

  List<LenderCashTransaction> findTop100ByCashAccountIdOrderByCreatedAtDescCashTransactionIdDesc(
      Long cashAccountId);
}
