package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.dto.request.ExecutionReportSubmission;
import com.hexaware.borrower_service.entity.BorrowerInventory;
import com.hexaware.borrower_service.entity.BorrowerOrder;
import com.hexaware.borrower_service.entity.BorrowerTradeActivity;
import com.hexaware.borrower_service.enums.BorrowerOrderSide;
import com.hexaware.borrower_service.enums.BorrowerOrderStatus;
import com.hexaware.borrower_service.enums.BorrowerTradeType;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.BorrowerInventoryRepository;
import com.hexaware.borrower_service.repository.BorrowerOrderRepository;
import com.hexaware.borrower_service.repository.BorrowerTradeActivityRepository;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import com.hexaware.borrower_service.service.orders.BorrowerTradeActivityService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BorrowerTradeActivityServiceTest {
  private static final String BORROWER_REFERENCE = "BORROWER-001";
  private static final String TRANSACTION_REFERENCE = "TX-001";
  private static final String SECURITY_ISIN = "INE002A01018";
  private static final String ORDER_REFERENCE = "ORDER-001";
  private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

  @Mock private BorrowerTradeActivityRepository trades;
  @Mock private BorrowerOrderRepository orders;
  @Mock private BorrowerInventoryRepository inventories;
  @Mock private LoanRequestSubmissionValidator validator;

  private BorrowerTradeActivityService service;
  private BorrowerOrder order;

  @BeforeEach
  void setUp() {
    service =
        new BorrowerTradeActivityService(
            trades,
            orders,
            inventories,
            validator,
            Clock.fixed(NOW, ZoneOffset.UTC));
    order = order(BorrowerOrderSide.SELL, 60L);
  }

  @Test
  void recordsPartialFillAndUpdatesTheOrderAveragePrice() {
    stubSellExecution("TRADE-001", 60L);
    when(trades.save(any(BorrowerTradeActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.recordExecution(report("TRADE-001", 40L, "102.50"));

    assertThat(response.tradeType()).isEqualTo(BorrowerTradeType.SELL_SHORT);
    assertThat(response.orderReference()).isEqualTo(ORDER_REFERENCE);
    assertThat(order.getFilledQuantity()).isEqualTo(40L);
    assertThat(order.getLeavesQuantity()).isEqualTo(20L);
    assertThat(order.getOrderStatus()).isEqualTo(BorrowerOrderStatus.PARTIALLY_FILLED);
    assertThat(order.getAverageFillPrice()).isEqualByComparingTo("102.500000");
  }

  @Test
  void completesOrderAndCalculatesWeightedAverageAcrossExecutions() {
    stubSellExecution("TRADE-002", 60L);
    when(trades.findByTradeReference("TRADE-003")).thenReturn(Optional.empty());
    when(trades.save(any(BorrowerTradeActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recordExecution(report("TRADE-002", 40L, "102.50"));
    var finalFill = service.recordExecution(report("TRADE-003", 20L, "103.00"));

    assertThat(finalFill.tradeType()).isEqualTo(BorrowerTradeType.SELL_SHORT);
    assertThat(order.getFilledQuantity()).isEqualTo(60L);
    assertThat(order.getLeavesQuantity()).isZero();
    assertThat(order.getOrderStatus()).isEqualTo(BorrowerOrderStatus.FILLED);
    assertThat(order.getAverageFillPrice()).isEqualByComparingTo("102.666667");
  }

  @Test
  void replaysAnIdenticalExecutionWithoutApplyingItTwice() {
    ExecutionReportSubmission report = report("TRADE-004", 10L, "102.50");
    when(trades.findByTradeReference("TRADE-004"))
        .thenReturn(Optional.of(trade(report)));

    var response = service.recordExecution(report);

    assertThat(response.tradeReference()).isEqualTo("TRADE-004");
    verify(orders, never()).findLockedByOrderReference(any());
    verify(trades, never()).save(any(BorrowerTradeActivity.class));
  }

  @Test
  void rejectsAnExecutionLargerThanTheOrderLeaves() {
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(inventory()));
    when(trades.findByTradeReference("TRADE-005")).thenReturn(Optional.empty());
    when(orders.findLockedByOrderReference(ORDER_REFERENCE)).thenReturn(Optional.of(order));
    order.setLeavesQuantity(5L);
    order.setFilledQuantity(55L);

    assertThatThrownBy(
            () -> service.recordExecution(report("TRADE-005", 6L, "102.50")))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("remaining quantity");
    verify(trades, never()).save(any(BorrowerTradeActivity.class));
  }

  @Test
  void recordsBuyExecutionAsCoverOnlyAgainstAnOutstandingShort() {
    order = order(BorrowerOrderSide.BUY, 40L);
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(inventory()));
    when(trades.findByTradeReference("TRADE-006")).thenReturn(Optional.empty());
    when(orders.findLockedByOrderReference(ORDER_REFERENCE)).thenReturn(Optional.of(order));
    when(orders.sumFilledQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.SELL))
        .thenReturn(40L);
    when(orders.sumFilledQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.BUY))
        .thenReturn(0L);
    when(trades.save(any(BorrowerTradeActivity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.recordExecution(report("TRADE-006", 40L, "98.00"));

    assertThat(response.tradeType()).isEqualTo(BorrowerTradeType.BUY_TO_COVER);
    assertThat(order.getOrderStatus()).isEqualTo(BorrowerOrderStatus.FILLED);
  }

  private void stubSellExecution(String tradeReference, long leavesQuantity) {
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(inventory()));
    when(trades.findByTradeReference(tradeReference)).thenReturn(Optional.empty());
    when(orders.findLockedByOrderReference(ORDER_REFERENCE)).thenReturn(Optional.of(order));
    when(orders.sumCommittedQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.SELL))
        .thenReturn(60L);
    when(orders.sumFilledQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.BUY))
        .thenReturn(0L);
    order.setLeavesQuantity(leavesQuantity);
    order.setFilledQuantity(order.getOrderQuantity() - leavesQuantity);
  }

  private ExecutionReportSubmission report(
      String tradeReference, long quantity, String price) {
    return new ExecutionReportSubmission(
        tradeReference,
        ORDER_REFERENCE,
        quantity,
        new BigDecimal(price),
        null,
        NOW.minusSeconds(10));
  }

  private BorrowerTradeActivity trade(ExecutionReportSubmission report) {
    return BorrowerTradeActivity.builder()
        .tradeReference(report.tradeReference())
        .orderReference(report.orderReference())
        .transactionReference(TRANSACTION_REFERENCE)
        .tradeType(BorrowerTradeType.SELL_SHORT)
        .quantity(report.quantity())
        .price(report.price())
        .tradeTime(report.tradeTime())
        .createdBy(7L)
        .build();
  }

  private BorrowerOrder order(BorrowerOrderSide side, long quantity) {
    return BorrowerOrder.builder()
        .orderReference(ORDER_REFERENCE)
        .borrowerOrganizationReference(BORROWER_REFERENCE)
        .securityIsin(SECURITY_ISIN)
        .transactionReference(TRANSACTION_REFERENCE)
        .side(side)
        .orderQuantity(quantity)
        .filledQuantity(0L)
        .leavesQuantity(quantity)
        .orderStatus(BorrowerOrderStatus.NEW)
        .version(0L)
        .createdBy(7L)
        .createdAt(NOW.minusSeconds(30))
        .updatedAt(NOW.minusSeconds(30))
        .build();
  }

  private BorrowerInventory inventory() {
    return BorrowerInventory.builder()
        .transactionReference(TRANSACTION_REFERENCE)
        .borrowerOrganizationReference(BORROWER_REFERENCE)
        .securityIsin(SECURITY_ISIN)
        .borrowedQuantity(100L)
        .sourceContractVersion(1L)
        .version(0L)
        .build();
  }
}
