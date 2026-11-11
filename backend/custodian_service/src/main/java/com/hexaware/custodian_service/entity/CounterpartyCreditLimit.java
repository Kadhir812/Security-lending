package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.RuleStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "counterparty_credit_limit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterpartyCreditLimit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "credit_limit_id")
  private Long creditLimitId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "msla_id", nullable = false, unique = true)
  private Long mslaId;

  @Column(name = "limit_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal limitAmount;

  @Builder.Default
  @Column(
      name = "used_amount",
      nullable = false,
      precision = 20,
      scale = 2,
      columnDefinition = "decimal(20,2) NOT NULL DEFAULT 0")
  private BigDecimal usedAmount = BigDecimal.ZERO;

  @Builder.Default
  @Column(
      name = "reserved_amount",
      nullable = false,
      precision = 20,
      scale = 2,
      columnDefinition = "decimal(20,2) NOT NULL DEFAULT 0")
  private BigDecimal reservedAmount = BigDecimal.ZERO;

  @Column(
      name = "available_amount",
      nullable = false,
      insertable = false,
      updatable = false,
      precision = 20,
      scale = 2,
      columnDefinition =
          "decimal(20,2) GENERATED ALWAYS AS (limit_amount-used_amount-reserved_amount) STORED")
  private BigDecimal availableAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "enum('ACTIVE','INACTIVE')")
  private RuleStatus status;
}
