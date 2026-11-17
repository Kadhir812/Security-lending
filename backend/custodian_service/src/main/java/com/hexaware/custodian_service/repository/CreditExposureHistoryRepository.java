package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.CreditExposureHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditExposureHistoryRepository
    extends JpaRepository<CreditExposureHistory, Long> {
  Optional<CreditExposureHistory> findByValuationResultReference(String reference);
  Optional<CreditExposureHistory> findByContractIdAndIdempotencyKey(
      Long contractId, String idempotencyKey);
}
