package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.BorrowerTradeActivity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerTradeActivityRepository extends JpaRepository<BorrowerTradeActivity, Long> {
  Optional<BorrowerTradeActivity> findByTradeReference(String tradeReference);

  List<BorrowerTradeActivity> findByTransactionReferenceOrderByTradeIdAsc(
      String transactionReference);

  List<BorrowerTradeActivity> findByBorrowerOrganizationReferenceAndSecurityIsinOrderByTradeIdAsc(
      String borrowerOrganizationReference, String securityIsin);

  List<BorrowerTradeActivity> findByBorrowerOrganizationReferenceOrderByTradeIdDesc(
      String borrowerOrganizationReference);
}
