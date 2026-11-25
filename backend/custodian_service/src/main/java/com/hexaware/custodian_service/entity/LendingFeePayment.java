package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "lending_fee_payment")
public class LendingFeePayment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "obligation_reference", nullable = false, length = 80)
  private String obligationReference;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "calculation_snapshot", nullable = false, columnDefinition = "json")
  private Map<String, Object> calculationSnapshot;

  @Column(name = "payment_reference", nullable = false, unique = true, length = 80)
  private String paymentReference;

  @Column(name = "calculation_result_reference", nullable = false, length = 36)
  private String calculationResultReference;

  @Column(name = "from_organization_id", nullable = false)
  private Long fromOrganizationId;

  @Column(name = "to_organization_id", nullable = false)
  private Long toOrganizationId;

  @Column(name = "payment_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal paymentAmount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 20)
  private String status;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "paid_at", columnDefinition = "datetime(6)")
  private Instant paidAt;

  @Column(name = "external_reference", unique = true, length = 150)
  private String externalReference;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;
}
