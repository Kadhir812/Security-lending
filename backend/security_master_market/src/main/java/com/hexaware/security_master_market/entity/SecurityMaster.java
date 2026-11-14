package com.hexaware.security_master_market.entity;

import com.hexaware.security_master_market.enums.SecurityStatus;
import com.hexaware.security_master_market.enums.SecurityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.sql.Types;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "security_master")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityMaster {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "security_id")
  private Long securityId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "symbol", nullable = false, length = 50)
  private String symbol;

  @Column(name = "security_name", nullable = false, length = 255)
  private String securityName;

  @JdbcTypeCode(Types.CHAR)
  @Column(
      name = "isin",
      nullable = false,
      unique = true,
      length = 12,
      columnDefinition = "char(12)",
      updatable = false)
  private String isin;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "security_type",
      nullable = false,
      columnDefinition = "enum('EQUITY','CORPORATE_BOND','GOVT_BOND','ETF')")
  private SecurityType securityType;

  @JdbcTypeCode(Types.CHAR)
  @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(name = "exchange_name", length = 100)
  private String exchangeName;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "enum('ACTIVE','INACTIVE')")
  private SecurityStatus status = SecurityStatus.ACTIVE;
}
