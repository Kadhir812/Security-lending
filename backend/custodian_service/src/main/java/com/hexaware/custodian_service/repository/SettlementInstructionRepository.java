package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface SettlementInstructionRepository extends JpaRepository<SettlementInstruction, Long> {
  Optional<SettlementInstruction> findByInstructionReference(String reference);
  List<SettlementInstruction> findByContractId(Long contractId);
  List<SettlementInstruction> findByContractIdAndStatusOrderBySettledAtAsc(
      Long contractId, SettlementInstructionStatus status);
  Optional<SettlementInstruction> findByExternalConfirmationReference(String reference);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SettlementInstruction s where s.instructionReference = :reference")
  Optional<SettlementInstruction> findLockedByInstructionReference(String reference);
}
