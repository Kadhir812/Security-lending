package com.hexaware.borrower_service.entity;

import com.hexaware.borrower_service.enums.BorrowerCashTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "borrower_cash_transaction")
public class BorrowerCashTransaction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "cash_transaction_id", nullable = false)
  private Long cashTransactionId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private BorrowerCashAccount account;

  @Column(name = "recorded_by_user_id")
  private Long recordedByUserId;

  @Column(name = "transaction_reference", nullable = false, unique = true, length = 150)
  private String transactionReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false)
  private BorrowerCashTransactionType transactionType;

  @Column(name = "activity_type", nullable = false, length = 80)
  private String activityType;

  @Column(name = "amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal amount;

  @Column(name = "related_reference_type", length = 40)
  private String relatedReferenceType;

  @Column(name = "related_reference", length = 80)
  private String relatedReference;

  @Column(name = "description", nullable = false, length = 255)
  private String description;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "transaction_time", nullable = false, columnDefinition = "datetime(6)")
  private Instant transactionTime;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "recorded_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant recordedAt;
}
