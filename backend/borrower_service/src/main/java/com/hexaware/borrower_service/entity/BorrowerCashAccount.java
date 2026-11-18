package com.hexaware.borrower_service.entity;

import com.hexaware.borrower_service.enums.BorrowerCashAccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "borrower_cash_account")
public class BorrowerCashAccount {
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "borrower_organization_reference", nullable = false, length = 80)
  private String borrowerOrganizationReference;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(name = "cash_balance", nullable = false, precision = 20, scale = 2)
  private BigDecimal cashBalance;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private BorrowerCashAccountStatus status = BorrowerCashAccountStatus.ACTIVE;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant createdAt;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant updatedAt;
}
