package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.CollateralHaircut;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CollateralHaircutRepository extends JpaRepository<CollateralHaircut, Long> {

  List<CollateralHaircut> findByMslaIdOrderByEffectiveFromDescHaircutIdDesc(Long mslaId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select haircut
      from CollateralHaircut haircut
      where haircut.haircutId = :haircutId and haircut.mslaId = :mslaId
      """)
  Optional<CollateralHaircut> findLocked(Long haircutId, Long mslaId);
}
