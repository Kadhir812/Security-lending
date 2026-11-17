package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
  Optional<InventoryReservation> findByContractId(Long contractId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from InventoryReservation r where r.contractId = :contractId")
  Optional<InventoryReservation> findLockedByContractId(Long contractId);
}
