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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lending_fee")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LendingFee {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "fee_id")
  private Long feeId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "msla_id", nullable = false)
  private Long mslaId;

  @Column(name = "security_isin", length = 12, columnDefinition = "char(12)")
  private String securityIsin;

  @Column(name = "annual_fee_pct", nullable = false, precision = 10, scale = 6)
  private BigDecimal annualFeePct;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "enum('ACTIVE','INACTIVE')")
  private RuleStatus status;
}
