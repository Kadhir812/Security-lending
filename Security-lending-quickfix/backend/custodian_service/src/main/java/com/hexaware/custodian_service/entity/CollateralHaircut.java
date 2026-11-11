package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.CollateralType;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "collateral_haircut")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollateralHaircut {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "haircut_id")
  private Long haircutId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "msla_id", nullable = false)
  private Long mslaId;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "collateral_type",
      nullable = false,
      columnDefinition = "enum('CASH','GOVT_BOND','CORPORATE_BOND','EQUITY','ETF')")
  private CollateralType collateralType;

  @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(name = "haircut_percentage", nullable = false, precision = 9, scale = 4)
  private BigDecimal haircutPercentage;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "effective_from", nullable = false, columnDefinition = "datetime(6)")
  private Instant effectiveFrom;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "effective_to", columnDefinition = "datetime(6)")
  private Instant effectiveTo;
}
