package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.LocateRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocateRequestRepository extends JpaRepository<LocateRequest, Long> {
  Optional<LocateRequest> findByLocateReferenceAndBorrowerOrganizationReference(
      String locateReference, String borrowerOrganizationReference);
}
