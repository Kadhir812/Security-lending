package com.hexaware.borrower_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "borrower_inventory")
public class BorrowerInventory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "inventory_id", nullable = false)
  private Long inventoryId;

  @Column(name = "transaction_reference", nullable = false, unique = true, length = 80)
  private String transactionReference;

  @Column(name = "borrower_organization_reference", nullable = false, length = 80)
  private String borrowerOrganizationReference;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "security_isin", nullable = false, length = 12, columnDefinition = "char(12)")
  private String securityIsin;

  @Column(name = "borrowed_quantity", nullable = false)
  private Long borrowedQuantity;

  @Column(name = "source_contract_version", nullable = false)
  private Long sourceContractVersion;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;
}
