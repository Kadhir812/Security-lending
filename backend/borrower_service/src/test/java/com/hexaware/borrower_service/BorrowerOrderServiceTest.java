package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.dto.request.BorrowerOrderSubmission;
import com.hexaware.borrower_service.entity.BorrowerInventory;
import com.hexaware.borrower_service.entity.BorrowerOrder;
import com.hexaware.borrower_service.enums.BorrowerOrderSide;
import com.hexaware.borrower_service.enums.BorrowerOrderStatus;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.BorrowerInventoryRepository;
import com.hexaware.borrower_service.repository.BorrowerOrderRepository;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import com.hexaware.borrower_service.service.orders.BorrowerOrderService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class BorrowerOrderServiceTest {
  private static final String BORROWER_REFERENCE = "BORROWER-001";
  private static final String TRANSACTION_REFERENCE = "TX-001";
  private static final String SECURITY_ISIN = "INE002A01018";
  private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

  @Mock private BorrowerOrderRepository orders;
  @Mock private BorrowerInventoryRepository inventories;
  @Mock private LoanRequestSubmissionValidator validator;

  private BorrowerOrderService service;
  private LocalUserAuthentication authentication;
  private BorrowerInventory inventory;

  @BeforeEach
  void setUp() {
    service =
        new BorrowerOrderService(
            orders, inventories, validator, Clock.fixed(NOW, ZoneOffset.UTC));
    Jwt token =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("trader-001")
            .issuedAt(NOW.minusSeconds(60))
            .expiresAt(NOW.plusSeconds(3600))
            .build();
    authentication =
        new LocalUserAuthentication(
            token,
            List.of(new SimpleGrantedAuthority("ROLE_TRADER")),
            7L,
            BORROWER_REFERENCE);
    inventory =
        BorrowerInventory.builder()
            .transactionReference(TRANSACTION_REFERENCE)
            .borrowerOrganizationReference(BORROWER_REFERENCE)
            .securityIsin(SECURITY_ISIN)
            .borrowedQuantity(100L)
            .sourceContractVersion(1L)
            .version(0L)
            .build();
    when(validator.requireBorrower(authentication)).thenReturn(authentication);
  }

  @Test
  void submitsSellOrderAgainstSettledInventoryAsNew() {
    BorrowerOrderSubmission submission = sellOrder(60L);
    stubInventoryAndReservations(0L);
    when(orders.findByOrderReferenceAndBorrowerOrganizationReference(
            "ORDER-001", BORROWER_REFERENCE))
        .thenReturn(Optional.empty());
    when(orders.save(any(BorrowerOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.submit(authentication, "ORDER-001", submission);

    assertThat(response.orderReference()).isEqualTo("ORDER-001");
    assertThat(response.orderStatus()).isEqualTo(BorrowerOrderStatus.NEW);
    assertThat(response.orderQuantity()).isEqualTo(60L);
    assertThat(response.leavesQuantity()).isEqualTo(60L);
    assertThat(response.createdAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsSellOrdersThatExceedUnreservedInventory() {
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(inventory));
    when(orders.findByOrderReferenceAndBorrowerOrganizationReference(
            "ORDER-002", BORROWER_REFERENCE))
        .thenReturn(Optional.empty());
    when(orders.sumFilledQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.SELL))
        .thenReturn(0L);
    when(orders.sumFilledQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.BUY))
        .thenReturn(0L);
    when(orders.sumCommittedQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.SELL))
        .thenReturn(75L);

    assertThatThrownBy(() -> service.submit(authentication, "ORDER-002", sellOrder(30L)))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("unreserved borrowed quantity");
    verify(orders, never()).save(any(BorrowerOrder.class));
  }

  @Test
  void replaysTheSameIdempotentSubmissionWithoutCreatingAnotherOrder() {
    BorrowerOrder existing =
        BorrowerOrder.builder()
            .orderReference("ORDER-003")
            .borrowerOrganizationReference(BORROWER_REFERENCE)
            .securityIsin(SECURITY_ISIN)
            .transactionReference(TRANSACTION_REFERENCE)
            .side(BorrowerOrderSide.SELL)
            .orderQuantity(60L)
            .filledQuantity(0L)
            .leavesQuantity(60L)
            .orderStatus(BorrowerOrderStatus.NEW)
            .version(0L)
            .createdAt(NOW)
            .updatedAt(NOW)
            .build();
    when(orders.findByOrderReferenceAndBorrowerOrganizationReference(
            "ORDER-003", BORROWER_REFERENCE))
        .thenReturn(Optional.of(existing));

    var response = service.submit(authentication, "ORDER-003", sellOrder(60L));

    assertThat(response.orderReference()).isEqualTo("ORDER-003");
    verify(inventories, never()).findLockedByTransactionReference(any());
    verify(orders, never()).save(any(BorrowerOrder.class));
  }

  private BorrowerOrderSubmission sellOrder(long quantity) {
    return new BorrowerOrderSubmission(
        TRANSACTION_REFERENCE, SECURITY_ISIN, BorrowerOrderSide.SELL, quantity);
  }

  private void stubInventoryAndReservations(long reservedQuantity) {
    when(inventories.findLockedByTransactionReference(TRANSACTION_REFERENCE))
        .thenReturn(Optional.of(inventory));
    when(orders.sumFilledQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.SELL))
        .thenReturn(0L);
    when(orders.sumFilledQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.BUY))
        .thenReturn(0L);
    when(orders.sumCommittedQuantity(TRANSACTION_REFERENCE, BorrowerOrderSide.SELL))
        .thenReturn(reservedQuantity);
  }
}
