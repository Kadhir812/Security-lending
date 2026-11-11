package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LocateRequest;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface LocateRequestRepository extends JpaRepository<LocateRequest, Long> {
  Optional<LocateRequest> findByLocateReference(String locateReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select l from LocateRequest l where l.locateReference = :reference")
  Optional<LocateRequest> findLockedByLocateReference(String reference);

  @Query("""
      select l from LocateRequest l, MslaAgreement a, LenderMandate m
      where l.mslaId = a.mslaId and a.mandateId = m.mandateId
        and m.custodianOrgId = :custodianId order by l.requestTime desc
      """)
  List<LocateRequest> findVisibleToCustodian(Long custodianId);

  @Query("""
      select l from LocateRequest l, MslaAgreement a, LenderMandate m
      where l.mslaId = a.mslaId and a.mandateId = m.mandateId
        and m.custodianOrgId = :custodianId and l.locateReference = :reference
      """)
  Optional<LocateRequest> findVisibleByReference(Long custodianId, String reference);
}
