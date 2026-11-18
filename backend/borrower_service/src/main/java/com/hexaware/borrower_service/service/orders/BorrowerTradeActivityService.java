package com.hexaware.borrower_service.service.orders;

import com.hexaware.borrower_service.dto.request.ExecutionReportSubmission;
import com.hexaware.borrower_service.dto.response.BorrowerTradeActivityResponse;
import com.hexaware.borrower_service.entity.BorrowerInventory;
import com.hexaware.borrower_service.entity.BorrowerOrder;
import com.hexaware.borrower_service.entity.BorrowerTradeActivity;
import com.hexaware.borrower_service.entity.LoanContractView;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.enums.BorrowerCashActivityType;
import com.hexaware.borrower_service.enums.BorrowerCashTransactionType;
import com.hexaware.borrower_service.enums.BorrowerOrderSide;
import com.hexaware.borrower_service.enums.BorrowerOrderStatus;
import com.hexaware.borrower_service.enums.BorrowerTradeType;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.BorrowerInventoryRepository;
import com.hexaware.borrower_service.repository.BorrowerOrderRepository;
import com.hexaware.borrower_service.repository.BorrowerTradeActivityRepository;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.service.cash.BorrowerCashService;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BorrowerTradeActivityService {
  private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}");
  private final BorrowerTradeActivityRepository trades;
  private final BorrowerOrderRepository orders;
  private final BorrowerInventoryRepository inventories;
  private final LoanContractViewRepository contractViews;
  private final LoanRequestRepository requests;
  private final BorrowerCashService cash;
  private final LoanRequestSubmissionValidator validator;
  private final Clock clock;

  @Transactional
  public BorrowerTradeActivityResponse recordExecution(ExecutionReportSubmission report) {
    validateReport(report);
    BorrowerTradeActivity existing =
        trades.findByTradeReference(report.tradeReference()).orElse(null);
    if (existing != null) {
      if (!sameReport(existing, report)) {
        throw BorrowerException.conflict(
            "Trade reference was reused with different execution details");
      }
      return response(existing);
    }

    BorrowerOrder order =
        orders
            .findLockedByOrderReference(report.orderReference())
            .orElseThrow(() -> BorrowerException.missing("Execution order not found"));
    if (order.getTransactionReference() == null) {
      throw BorrowerException.conflict("Execution order is not linked to a loan transaction");
    }
    BorrowerInventory inventory =
        inventories
            .findLockedByTransactionReference(order.getTransactionReference())
            .filter(
                position ->
                    order.getBorrowerOrganizationReference()
                            .equals(position.getBorrowerOrganizationReference())
                        && order.getSecurityIsin().equals(position.getSecurityIsin())
                        && position.getBorrowedQuantity() != null
                        && position.getBorrowedQuantity() > 0)
            .orElseThrow(
                () -> BorrowerException.conflict("Execution order has no matching settled inventory"));

    if (order.getOrderStatus() != BorrowerOrderStatus.NEW
        && order.getOrderStatus() != BorrowerOrderStatus.PARTIALLY_FILLED) {
      throw BorrowerException.conflict("Execution order is not open");
    }
    if (report.quantity() > order.getLeavesQuantity()) {
      throw BorrowerException.conflict("Execution quantity exceeds the order's remaining quantity");
    }
    if (report.tradeTime().isBefore(order.getCreatedAt())) {
      throw BorrowerException.invalid("Execution time precedes order creation");
    }

    BorrowerTradeType tradeType =
        order.getSide() == BorrowerOrderSide.SELL
            ? BorrowerTradeType.SELL_SHORT
            : BorrowerTradeType.BUY_TO_COVER;
    if (tradeType == BorrowerTradeType.SELL_SHORT) {
      long committedSells =
          orders.sumCommittedQuantity(
              order.getTransactionReference(), BorrowerOrderSide.SELL);
      long filledBuys =
          orders.sumFilledQuantity(order.getTransactionReference(), BorrowerOrderSide.BUY);
      if (committedSells > inventory.getBorrowedQuantity() + filledBuys) {
        throw BorrowerException.conflict(
            "Execution exceeds the remaining settled borrowed quantity");
      }
    } else {
      long filledSells =
          orders.sumFilledQuantity(order.getTransactionReference(), BorrowerOrderSide.SELL);
      long filledBuys =
          orders.sumFilledQuantity(order.getTransactionReference(), BorrowerOrderSide.BUY);
      if (report.quantity() > filledSells - filledBuys) {
        throw BorrowerException.conflict(
            "Buy-to-cover execution exceeds the outstanding short quantity");
      }
    }

    long previousFilled = order.getFilledQuantity();
    long newFilled = previousFilled + report.quantity();
    long newLeaves = order.getLeavesQuantity() - report.quantity();
    BigDecimal previousValue =
        order.getAverageFillPrice() == null
            ? BigDecimal.ZERO
            : order.getAverageFillPrice().multiply(BigDecimal.valueOf(previousFilled));
    BigDecimal newAverage =
        previousValue
            .add(report.price().multiply(BigDecimal.valueOf(report.quantity())))
            .divide(BigDecimal.valueOf(newFilled), 6, RoundingMode.HALF_UP);

    order.setFilledQuantity(newFilled);
    order.setLeavesQuantity(newLeaves);
    order.setAverageFillPrice(newAverage);
    order.setOrderStatus(
        newLeaves == 0 ? BorrowerOrderStatus.FILLED : BorrowerOrderStatus.PARTIALLY_FILLED);
    order.setUpdatedAt(clock.instant());

    BorrowerTradeActivity trade =
        BorrowerTradeActivity.builder()
            .tradeReference(report.tradeReference())
            .orderReference(order.getOrderReference())
            .transactionReference(order.getTransactionReference())
            .tradeType(tradeType)
            .quantity(report.quantity())
            .price(report.price())
            .realizedProfitLoss(report.realizedProfitLoss())
            .tradeTime(report.tradeTime())
            .createdBy(order.getCreatedBy())
            .version(0L)
            .build();
    BorrowerTradeActivity saved = trades.save(trade);
    recordTradeCash(order, report, tradeType);
    return response(saved);
  }

  @Transactional(readOnly = true)
  public List<BorrowerTradeActivityResponse> listForOrder(
      Authentication authentication, String orderReference) {
    var borrower = validator.requireBorrower(authentication);
    if (orders
        .findByOrderReferenceAndBorrowerOrganizationReference(
            orderReference, borrower.getOrganizationReference())
        .isEmpty()) {
      throw BorrowerException.missing("Borrower order not found");
    }
    return trades.findByOrderReferenceOrderByTradeTimeAsc(orderReference).stream()
        .map(this::response)
        .toList();
  }

  private void validateReport(ExecutionReportSubmission report) {
    if (report == null
        || report.tradeReference() == null
        || !REFERENCE.matcher(report.tradeReference()).matches()
        || report.orderReference() == null
        || !REFERENCE.matcher(report.orderReference()).matches()
        || report.quantity() == null
        || report.quantity() <= 0
        || report.price() == null
        || report.price().signum() <= 0
        || report.tradeTime() == null
        || report.tradeTime().isAfter(clock.instant())) {
      throw BorrowerException.invalid("Invalid execution report");
    }
    try {
      report.price().setScale(6, RoundingMode.UNNECESSARY);
      if (report.price().precision() > 20) {
        throw BorrowerException.invalid("Execution price exceeds supported precision");
      }
      if (report.realizedProfitLoss() != null) {
        report.realizedProfitLoss().setScale(2, RoundingMode.UNNECESSARY);
        if (report.realizedProfitLoss().precision() > 20) {
          throw BorrowerException.invalid("Realized P/L exceeds supported precision");
        }
      }
    } catch (ArithmeticException exception) {
      throw BorrowerException.invalid("Execution amounts exceed supported precision");
    }
  }

  private boolean sameReport(
      BorrowerTradeActivity existing, ExecutionReportSubmission report) {
    return Objects.equals(existing.getOrderReference(), report.orderReference())
        && Objects.equals(existing.getQuantity(), report.quantity())
        && existing.getPrice().compareTo(report.price()) == 0
        && sameAmount(existing.getRealizedProfitLoss(), report.realizedProfitLoss())
        && Objects.equals(existing.getTradeTime(), report.tradeTime());
  }

  private boolean sameAmount(BigDecimal existing, BigDecimal incoming) {
    return existing == null
        ? incoming == null
        : incoming != null && existing.compareTo(incoming) == 0;
  }

  private void recordTradeCash(
      BorrowerOrder order, ExecutionReportSubmission report, BorrowerTradeType tradeType) {
    LoanContractView contract =
        contractViews
            .findByTransactionReference(order.getTransactionReference())
            .orElseThrow(() -> BorrowerException.conflict("Trade contract is unavailable"));
    LoanRequest request =
        requests
            .findByRequestReferenceAndBorrowerOrganizationReference(
                contract.getRequestReference(), order.getBorrowerOrganizationReference())
            .orElseThrow(() -> BorrowerException.conflict("Trade loan request is unavailable"));
    if (request.getSettlementCurrency() == null) {
      throw BorrowerException.conflict("Trade loan request has no settlement currency");
    }
    BigDecimal amount =
        report
            .price()
            .multiply(BigDecimal.valueOf(report.quantity()))
            .setScale(2, RoundingMode.HALF_UP);
    if (amount.signum() <= 0 || amount.precision() > 20) {
      throw BorrowerException.invalid("Trade cash amount is outside supported precision");
    }
    boolean shortSale = tradeType == BorrowerTradeType.SELL_SHORT;
    cash.recordTransaction(
        order.getBorrowerOrganizationReference(),
        request.getSettlementCurrency(),
        "CASH:TRADE:" + report.tradeReference(),
        shortSale ? BorrowerCashTransactionType.CREDIT : BorrowerCashTransactionType.DEBIT,
        shortSale
            ? BorrowerCashActivityType.SHORT_SALE_PROCEEDS
            : BorrowerCashActivityType.BUY_TO_COVER_PAYMENT,
        amount,
        "TRADE",
        report.tradeReference(),
        shortSale ? "Short-sale proceeds" : "Buy-to-cover payment",
        report.tradeTime());
  }

  private BorrowerTradeActivityResponse response(BorrowerTradeActivity trade) {
    return new BorrowerTradeActivityResponse(
        trade.getTradeReference(),
        trade.getOrderReference(),
        trade.getTransactionReference(),
        trade.getTradeType(),
        trade.getQuantity(),
        trade.getPrice(),
        trade.getRealizedProfitLoss(),
        trade.getTradeTime());
  }
}
