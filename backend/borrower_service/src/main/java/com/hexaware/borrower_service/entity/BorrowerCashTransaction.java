package com.hexaware.borrower_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "borrower_cash_transaction")
public class BorrowerCashTransaction {
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "cash_transaction_id", nullable = false)
  private Long cashTransactionId;

  @Column(name = "transaction_reference", nullable = false, unique = true, length = 80)
  private String transactionReference;

  @Column(name = "cash_account_id", nullable = false)
  private Long cashAccountId;

  @Column(name = "transaction_type", nullable = false, length = 40)
  private String transactionType;

  @Column(name = "source_type", nullable = false, length = 20)
  private String sourceType;

  @Column(name = "source_reference", nullable = false, length = 100)
  private String sourceReference;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  @Column(name = "cash_delta", nullable = false, precision = 20, scale = 2)
  private BigDecimal cashDelta;

  @Column(name = "reserved_delta", nullable = false, precision = 20, scale = 2)
  private BigDecimal reservedDelta;

  @Column(name = "cash_balance_after", nullable = false, precision = 20, scale = 2)
  private BigDecimal cashBalanceAfter;

  @Column(name = "reserved_balance_after", nullable = false, precision = 20, scale = 2)
  private BigDecimal reservedBalanceAfter;

  @Column(name = "description", length = 500)
  private String description;

  @Column(name = "created_by_user_id")
  private Long createdByUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
