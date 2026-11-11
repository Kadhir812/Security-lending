package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.Collateral;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface CollateralRepository extends JpaRepository<Collateral, Long> {
  Optional<Collateral> findByCollateralReference(String reference);
  List<Collateral> findByContractId(Long contractId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Collateral c where c.contractId = :contractId")
  List<Collateral> findLockedByContractId(Long contractId);
}
