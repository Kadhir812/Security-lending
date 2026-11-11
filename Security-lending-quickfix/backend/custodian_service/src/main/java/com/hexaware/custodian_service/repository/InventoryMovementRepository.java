package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
  boolean existsBySettlementInstructionId(Long settlementInstructionId);
}
