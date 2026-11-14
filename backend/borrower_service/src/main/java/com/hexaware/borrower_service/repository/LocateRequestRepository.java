package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.LocateRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocateRequestRepository extends JpaRepository<LocateRequest, Long> {
  Optional<LocateRequest> findByLocateReferenceAndBorrowerOrganizationReference(
      String locateReference, String borrowerOrganizationReference);

  List<LocateRequest> findByBorrowerOrganizationReferenceOrderByRequestTimeDesc(
      String borrowerOrganizationReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select locate from LocateRequest locate where locate.locateId = :locateId")
  Optional<LocateRequest> findLockedByLocateId(Long locateId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select locate from LocateRequest locate where locate.locateReference = :reference")
  Optional<LocateRequest> findLockedByLocateReference(String reference);
}
