package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.RecallRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RecallRequestRepository extends JpaRepository<RecallRequest, Long> {
  Optional<RecallRequest> findByRecallReference(String reference);

  List<RecallRequest> findByContractIdOrderByRequestedAtDesc(Long contractId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select recall from RecallRequest recall where recall.recallReference = :reference")
  Optional<RecallRequest> findLockedByRecallReference(String reference);
}
