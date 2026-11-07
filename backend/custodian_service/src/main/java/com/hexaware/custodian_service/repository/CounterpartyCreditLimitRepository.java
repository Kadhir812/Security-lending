package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.CounterpartyCreditLimit;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CounterpartyCreditLimitRepository
    extends JpaRepository<CounterpartyCreditLimit, Long> {

  Optional<CounterpartyCreditLimit> findByMslaId(Long mslaId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select creditLimit
      from CounterpartyCreditLimit creditLimit
      where creditLimit.mslaId = :mslaId
      """)
  Optional<CounterpartyCreditLimit> findLockedByMslaId(Long mslaId);

  @Query(
      """
      select creditLimit
      from CounterpartyCreditLimit creditLimit, MslaAgreement agreement, LenderMandate mandate
      where creditLimit.mslaId = agreement.mslaId
        and agreement.mandateId = mandate.mandateId
        and mandate.custodianOrgId = :custodianId
      """)
  Page<CounterpartyCreditLimit> findVisibleToCustodian(Long custodianId, Pageable pageable);
}
