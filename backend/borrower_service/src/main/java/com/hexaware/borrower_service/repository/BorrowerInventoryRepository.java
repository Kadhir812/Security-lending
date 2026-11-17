package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.BorrowerInventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BorrowerInventoryRepository extends JpaRepository<BorrowerInventory, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select inventory from BorrowerInventory inventory "
      + "where inventory.transactionReference = :transactionReference")
  Optional<BorrowerInventory> findLockedByTransactionReference(String transactionReference);
}
