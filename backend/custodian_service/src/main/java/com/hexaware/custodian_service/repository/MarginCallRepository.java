package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.MarginCall;
import com.hexaware.custodian_service.enums.MarginCallStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface MarginCallRepository extends JpaRepository<MarginCall, Long> {
  Optional<MarginCall> findByMarginCallReference(String reference);
  Optional<MarginCall> findByValuationResultReference(String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from MarginCall m where m.contractId = :contractId and m.status in :statuses")
  List<MarginCall> findActiveLocked(Long contractId, List<MarginCallStatus> statuses);

  List<MarginCall> findByContractIdOrderByCreatedAtDesc(Long contractId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from MarginCall m where m.marginCallId = :id")
  Optional<MarginCall> findLockedById(Long id);

  @Query("""
      select m.marginCallId from MarginCall m
      where m.status in :statuses and m.dueAt <= :asOf
      order by m.dueAt
      """)
  List<Long> findDueIds(List<MarginCallStatus> statuses, Instant asOf);
}
