package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
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
@Table(name = "inventory_movement")
public class InventoryMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "movement_id", nullable = false)
  private Long movementId;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "settlement_instruction_id", nullable = false, unique = true)
  private Long settlementInstructionId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "security_isin", nullable = false, length = 12, columnDefinition = "char(12)")
  private String securityIsin;

  @Column(name = "from_organization_id", nullable = false)
  private Long fromOrganizationId;

  @Column(name = "to_organization_id", nullable = false)
  private Long toOrganizationId;

  @Column(nullable = false)
  private Long quantity;

  @Column(name = "movement_type", nullable = false, length = 40)
  private String movementType;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "movement_timestamp", nullable = false, columnDefinition = "datetime(6)")
  private Instant movementTimestamp;
}
