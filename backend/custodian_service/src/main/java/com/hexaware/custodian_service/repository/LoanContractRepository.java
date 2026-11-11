package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

public interface LoanContractRepository extends JpaRepository<LoanContract, Long> {
  Optional<LoanContract> findByLoanRequestId(Long loanRequestId);
  Optional<LoanContract> findByTransactionReference(String transactionReference);
  List<LoanContract> findByContractStatus(LoanContractStatus status);
  List<LoanContract> findByContractStatusIn(List<LoanContractStatus> statuses);

  @Query("""
      select c from LoanContract c, LoanRequest r, MslaAgreement a, LenderMandate m
      where c.loanRequestId = r.requestId
        and r.mslaId = a.mslaId
        and a.mandateId = m.mandateId
        and m.custodianOrgId = :custodianId
      order by c.contractCreatedAt desc
      """)
  Page<LoanContract> findByCustodian(Long custodianId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from LoanContract c where c.transactionReference = :transactionReference")
  Optional<LoanContract> findLockedByTransactionReference(String transactionReference);
}
