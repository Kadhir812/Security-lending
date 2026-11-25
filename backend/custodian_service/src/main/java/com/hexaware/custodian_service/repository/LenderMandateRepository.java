package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LenderMandate;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LenderMandateRepository
    extends JpaRepository<LenderMandate, Long>, JpaSpecificationExecutor<LenderMandate> {

  Optional<LenderMandate> findByMandateReferenceAndCustodianOrgId(
      String reference, Long custodian);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select mandate
      from LenderMandate mandate
      where mandate.mandateReference = :reference
        and mandate.custodianOrgId = :custodian
      """)
  Optional<LenderMandate> findLocked(String reference, Long custodian);
}
