package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LendingFee;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LendingFeeRepository extends JpaRepository<LendingFee, Long> {

  List<LendingFee> findByMslaIdOrderByEffectiveFromDescFeeIdDesc(Long mslaId);

  Optional<LendingFee> findByFeeIdAndMslaId(Long feeId, Long mslaId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select fee
      from LendingFee fee
      where fee.feeId = :feeId and fee.mslaId = :mslaId
      """)
  Optional<LendingFee> findLocked(Long feeId, Long mslaId);
}
