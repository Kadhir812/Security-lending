package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "settlement_instruction")
public class SettlementInstruction {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "instruction_id")
  private Long instructionId;
  @Version @Column(nullable = false)
  private Long version;
  @Column(name = "instruction_reference", nullable = false, unique = true, length = 80)
  private String instructionReference;
  @Column(name = "contract_id", nullable = false)
  private Long contractId;
  @Column(name = "collateral_id")
  private Long collateralId;
  @Column(name = "recall_id")
  private Long recallId;
  @Column(name = "margin_call_id")
  private Long marginCallId;
  @Column(name = "instruction_type", nullable = false)
  private String instructionType;
  @Column(name = "from_organization_id", nullable = false)
  private Long fromOrganizationId;
  @Column(name = "to_organization_id", nullable = false)
  private Long toOrganizationId;
  @Column(name = "from_account", nullable = false, length = 255)
  private String fromAccount;
  @Column(name = "to_account", nullable = false, length = 255)
  private String toAccount;
  @Column(name = "security_isin", length = 12)
  private String securityIsin;
  @Column(precision = 24, scale = 6)
  private BigDecimal quantity;
  @Column(precision = 20, scale = 2)
  private BigDecimal amount;
  @Column(nullable = false, length = 3)
  private String currency;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SettlementInstructionStatus status;
  @Column(name = "settlement_date", nullable = false)
  private LocalDate settlementDate;
  @Column(name = "settled_at")
  private Instant settledAt;
  @Column(name = "external_confirmation_reference", unique = true, length = 150)
  private String externalConfirmationReference;
  @Column(name = "failure_reason", length = 500)
  private String failureReason;
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
