package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.RebatePaymentHistory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RebatePaymentHistoryRepository
    extends JpaRepository<RebatePaymentHistory, Long> {
  List<RebatePaymentHistory> findByContractIdOrderByPaymentId(Long contractId);

  Optional<RebatePaymentHistory> findByPaymentReference(String paymentReference);

  Optional<RebatePaymentHistory> findByExternalReference(String externalReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select payment from RebatePaymentHistory payment "
          + "where payment.contractId = :contractId")
  List<RebatePaymentHistory> findLockedByContractId(Long contractId);
}
