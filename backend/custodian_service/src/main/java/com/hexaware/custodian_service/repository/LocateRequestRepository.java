package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LocateRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocateRequestRepository extends JpaRepository<LocateRequest, Long> {
  Optional<LocateRequest> findByLocateReference(String locateReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select locate from LocateRequest locate where locate.locateReference = :reference")
  Optional<LocateRequest> findLockedByLocateReference(String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select locate from LocateRequest locate where locate.locateId = :locateId")
  Optional<LocateRequest> findLockedByLocateId(Long locateId);

  @Query(
      """
      select locate
      from LocateRequest locate, MslaAgreement agreement, LenderMandate mandate
      where locate.mslaId = agreement.mslaId
        and agreement.mandateId = mandate.mandateId
        and mandate.custodianOrgId = :custodianId
        and locate.status = 'PENDING'
      order by locate.requestTime desc, locate.locateId desc
      """)
  List<LocateRequest> findPendingByCustodian(Long custodianId);
}
