package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lender_inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LenderInventory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "inventory_id")
  private Long inventoryId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "security_isin", nullable = false, length = 12, columnDefinition = "char(12)")
  private String securityIsin;

  @Column(name = "total_quantity", nullable = false)
  private Long totalQuantity;

  @Builder.Default
  @Column(
      name = "on_loan_quantity",
      nullable = false,
      columnDefinition = "bigint NOT NULL DEFAULT 0")
  private Long onLoanQuantity = 0L;

  @Builder.Default
  @Column(
      name = "reserved_quantity",
      nullable = false,
      columnDefinition = "bigint NOT NULL DEFAULT 0")
  private Long reservedQuantity = 0L;

  @Column(
      name = "available_quantity",
      nullable = false,
      insertable = false,
      updatable = false,
      columnDefinition =
          "bigint GENERATED ALWAYS AS (total_quantity-on_loan_quantity-reserved_quantity) STORED")
  private Long availableQuantity;
}
