package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.LoanRequest;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {
  Optional<LoanRequest> findByRequestReference(String requestReference);

  Optional<LoanRequest> findByRequestReferenceAndBorrowerOrganizationReference(
      String requestReference, String borrowerOrganizationReference);

  Page<LoanRequest> findByBorrowerOrganizationReferenceOrderByRequestedAtDesc(
      String borrowerOrganizationReference, Pageable pageable);
}
