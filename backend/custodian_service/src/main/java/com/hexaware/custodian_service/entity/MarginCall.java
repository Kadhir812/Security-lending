package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.MarginCallStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "margin_call")
public class MarginCall {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "margin_call_id")
  private Long marginCallId;

  @Version
  @Column(nullable = false)
  private Long version;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "calculation_snapshot", nullable = false, columnDefinition = "json")
  private Map<String, Object> calculationSnapshot;

  @Column(name = "margin_call_reference", nullable = false, unique = true, length = 80)
  private String marginCallReference;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "valuation_result_reference", nullable = false, length = 36)
  private String valuationResultReference;

  @Column(name = "required_collateral", nullable = false, precision = 20, scale = 2)
  private BigDecimal requiredCollateral;

  @Column(name = "current_collateral", nullable = false, precision = 20, scale = 2)
  private BigDecimal currentCollateral;

  @Column(name = "shortfall_amount", insertable = false, updatable = false, precision = 20, scale = 2)
  private BigDecimal shortfallAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MarginCallStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;
}
