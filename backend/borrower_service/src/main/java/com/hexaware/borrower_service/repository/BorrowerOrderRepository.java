package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.BorrowerOrder;
import com.hexaware.borrower_service.enums.BorrowerOrderSide;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowerOrderRepository extends JpaRepository<BorrowerOrder, Long> {
  Optional<BorrowerOrder> findByOrderReferenceAndBorrowerOrganizationReference(
      String orderReference, String borrowerOrganizationReference);

  List<BorrowerOrder> findByBorrowerOrganizationReferenceOrderByCreatedAtDesc(
      String borrowerOrganizationReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select borrowerOrder from BorrowerOrder borrowerOrder "
      + "where borrowerOrder.orderReference = :orderReference "
      + "and borrowerOrder.borrowerOrganizationReference = :organizationReference")
  Optional<BorrowerOrder> findLockedByOrderReferenceAndBorrowerOrganizationReference(
      @Param("orderReference") String orderReference,
      @Param("organizationReference") String organizationReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select borrowerOrder from BorrowerOrder borrowerOrder "
      + "where borrowerOrder.orderReference = :orderReference")
  Optional<BorrowerOrder> findLockedByOrderReference(
      @Param("orderReference") String orderReference);

  @Query("select coalesce(sum(borrowerOrder.filledQuantity + borrowerOrder.leavesQuantity), 0) "
      + "from BorrowerOrder borrowerOrder "
      + "where borrowerOrder.transactionReference = :transactionReference "
      + "and borrowerOrder.side = :side")
  long sumCommittedQuantity(
      @Param("transactionReference") String transactionReference,
      @Param("side") BorrowerOrderSide side);

  @Query("select coalesce(sum(borrowerOrder.filledQuantity), 0) "
      + "from BorrowerOrder borrowerOrder "
      + "where borrowerOrder.transactionReference = :transactionReference "
      + "and borrowerOrder.side = :side")
  long sumFilledQuantity(
      @Param("transactionReference") String transactionReference,
      @Param("side") BorrowerOrderSide side);

  @Query("select coalesce(sum(borrowerOrder.leavesQuantity), 0) "
      + "from BorrowerOrder borrowerOrder "
      + "where borrowerOrder.transactionReference = :transactionReference "
      + "and borrowerOrder.side = :side")
  long sumLeavesQuantity(
      @Param("transactionReference") String transactionReference,
      @Param("side") BorrowerOrderSide side);
}
