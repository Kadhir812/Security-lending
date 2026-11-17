package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LendingFeePayment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LendingFeePaymentRepository extends JpaRepository<LendingFeePayment, Long> {
  List<LendingFeePayment> findByContractIdOrderByPaymentId(Long contractId);
  Optional<LendingFeePayment> findByPaymentReference(String paymentReference);
  Optional<LendingFeePayment> findByExternalReference(String externalReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select payment from LendingFeePayment payment where payment.contractId = :contractId")
  List<LendingFeePayment> findLockedByContractId(Long contractId);
}
