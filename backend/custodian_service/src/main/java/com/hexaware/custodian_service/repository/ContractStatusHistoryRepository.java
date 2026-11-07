package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.ContractStatusHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractStatusHistoryRepository
    extends JpaRepository<ContractStatusHistory, Long> {
  Optional<ContractStatusHistory> findByContractIdAndSourceEventId(
      Long contractId, String sourceEventId);
}
