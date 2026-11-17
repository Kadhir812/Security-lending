package com.hexaware.borrower_service.service.orders;

import com.hexaware.borrower_service.dto.request.BorrowerOrderSubmission;
import com.hexaware.borrower_service.dto.response.BorrowerOrderResponse;
import com.hexaware.borrower_service.entity.BorrowerOrder;
import com.hexaware.borrower_service.entity.BorrowerInventory;
import com.hexaware.borrower_service.enums.BorrowerOrderSide;
import com.hexaware.borrower_service.enums.BorrowerOrderStatus;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.BorrowerInventoryRepository;
import com.hexaware.borrower_service.repository.BorrowerOrderRepository;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BorrowerOrderService {
  private final BorrowerOrderRepository orders;
  private final BorrowerInventoryRepository inventories;
  private final LoanRequestSubmissionValidator validator;
  private final Clock clock;

  @Transactional
  public BorrowerOrderResponse submit(
      Authentication authentication,
      String orderReference,
      BorrowerOrderSubmission submission) {
    var borrower = validator.requireBorrower(authentication);
    validator.validateIdempotencyKey(orderReference);

    BorrowerOrder existing =
        orders
            .findByOrderReferenceAndBorrowerOrganizationReference(
                orderReference, borrower.getOrganizationReference())
            .orElse(null);
    if (existing != null) {
      if (!sameSubmission(existing, submission)) {
        throw BorrowerException.conflict(
            "Order idempotency key was reused with different inputs");
      }
      return response(existing);
    }

    BorrowerInventory inventory =
        inventories
            .findLockedByTransactionReference(submission.transactionReference())
            .filter(
                position ->
                    borrower.getOrganizationReference()
                            .equals(position.getBorrowerOrganizationReference())
                        && submission.securityIsin().equals(position.getSecurityIsin())
                        && position.getBorrowedQuantity() != null
                        && position.getBorrowedQuantity() > 0)
            .orElseThrow(
                () ->
                    BorrowerException.invalid(
                        "No settled inventory matches this transaction and security"));

    if (submission.orderQuantity() > inventory.getBorrowedQuantity()) {
      throw BorrowerException.conflict(
          "Order quantity exceeds the settled borrowed quantity");
    }
    long filledSells =
        orders.sumFilledQuantity(submission.transactionReference(), BorrowerOrderSide.SELL);
    long filledBuys =
        orders.sumFilledQuantity(submission.transactionReference(), BorrowerOrderSide.BUY);
    if (submission.side() == BorrowerOrderSide.SELL) {
      long committedSells =
          orders.sumCommittedQuantity(submission.transactionReference(), BorrowerOrderSide.SELL);
      long availableToSell =
          inventory.getBorrowedQuantity() - committedSells + filledBuys;
      if (submission.orderQuantity() > availableToSell) {
        throw BorrowerException.conflict(
            "Sell order quantity exceeds the unreserved borrowed quantity");
      }
    } else {
      long pendingBuyToCover =
          orders.sumLeavesQuantity(submission.transactionReference(), BorrowerOrderSide.BUY);
      long availableToCover = filledSells - filledBuys - pendingBuyToCover;
      if (submission.orderQuantity() > availableToCover) {
        throw BorrowerException.conflict(
            "Buy order quantity exceeds the outstanding short quantity");
      }
    }

    var now = clock.instant();
    BorrowerOrder order =
        BorrowerOrder.builder()
            .orderReference(orderReference)
            .borrowerOrganizationReference(borrower.getOrganizationReference())
            .securityIsin(submission.securityIsin())
            .transactionReference(submission.transactionReference())
            .side(submission.side())
            .orderQuantity(submission.orderQuantity())
            .filledQuantity(0L)
            .leavesQuantity(submission.orderQuantity())
            .orderStatus(BorrowerOrderStatus.NEW)
            .version(0L)
            .createdBy(borrower.getUserId())
            .createdAt(now)
            .updatedAt(now)
            .build();
    return response(orders.save(order));
  }

  @Transactional(readOnly = true)
  public List<BorrowerOrderResponse> list(Authentication authentication) {
    var borrower = validator.requireBorrower(authentication);
    return orders
        .findByBorrowerOrganizationReferenceOrderByCreatedAtDesc(
            borrower.getOrganizationReference())
        .stream()
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public BorrowerOrderResponse get(Authentication authentication, String orderReference) {
    var borrower = validator.requireBorrower(authentication);
    return orders
        .findByOrderReferenceAndBorrowerOrganizationReference(
            orderReference, borrower.getOrganizationReference())
        .map(this::response)
        .orElseThrow(() -> BorrowerException.missing("Borrower order not found"));
  }

  @Transactional
  public BorrowerOrderResponse cancel(Authentication authentication, String orderReference) {
    var borrower = validator.requireBorrower(authentication);
    BorrowerOrder order =
        orders
            .findLockedByOrderReferenceAndBorrowerOrganizationReference(
                orderReference, borrower.getOrganizationReference())
            .orElseThrow(() -> BorrowerException.missing("Borrower order not found"));
    if (order.getOrderStatus() != BorrowerOrderStatus.NEW
        && order.getOrderStatus() != BorrowerOrderStatus.PARTIALLY_FILLED) {
      throw BorrowerException.conflict("Only an open order can be cancelled");
    }
    order.setOrderStatus(BorrowerOrderStatus.CANCELLED);
    order.setLeavesQuantity(0L);
    order.setUpdatedAt(clock.instant());
    return response(order);
  }

  private boolean sameSubmission(
      BorrowerOrder existing, BorrowerOrderSubmission submission) {
    return Objects.equals(existing.getTransactionReference(), submission.transactionReference())
        && Objects.equals(existing.getSecurityIsin(), submission.securityIsin())
        && existing.getSide() == submission.side()
        && Objects.equals(existing.getOrderQuantity(), submission.orderQuantity());
  }

  private BorrowerOrderResponse response(BorrowerOrder order) {
    return new BorrowerOrderResponse(
        order.getOrderReference(),
        order.getBorrowerOrganizationReference(),
        order.getSecurityIsin(),
        order.getTransactionReference(),
        order.getSide(),
        order.getOrderQuantity(),
        order.getFilledQuantity(),
        order.getLeavesQuantity(),
        order.getAverageFillPrice(),
        order.getOrderStatus(),
        order.getVersion(),
        order.getCreatedAt(),
        order.getUpdatedAt());
  }
}
