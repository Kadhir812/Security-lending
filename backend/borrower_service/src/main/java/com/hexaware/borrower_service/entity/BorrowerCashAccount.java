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
@Table(name = "borrower_cash_account")
public class BorrowerCashAccount {
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "cash_account_id", nullable = false)
  private Long cashAccountId;

  @Column(name = "account_reference", nullable = false, unique = true, length = 80)
  private String accountReference;

  @Column(name = "borrower_organization_reference", nullable = false, length = 80)
  private String borrowerOrganizationReference;

  @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(name = "cash_balance", nullable = false, precision = 20, scale = 2)
  private BigDecimal cashBalance;

  @Column(name = "reserved_balance", nullable = false, precision = 20, scale = 2)
  private BigDecimal reservedBalance;

  @Column(name = "available_balance", insertable = false, updatable = false,
      precision = 20, scale = 2)
  private BigDecimal availableBalance;

  @Column(name = "status", nullable = false, length = 10)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
