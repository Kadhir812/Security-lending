package com.hexaware.security_master_market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Immutable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "security_price",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"security_id", "price_timestamp", "source_name"}))
public class SecurityPrice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "price_id")
  private Long priceId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @JdbcTypeCode(Types.CHAR)
  @Column(
      name = "price_reference",
      nullable = false,
      unique = true,
      length = 36,
      columnDefinition = "char(36)")
  private String priceReference;

  @Column(name = "security_id", nullable = false)
  private Long securityId;

  @Column(name = "market_price", nullable = false, precision = 20, scale = 6)
  private BigDecimal marketPrice;

  @JdbcTypeCode(Types.TIMESTAMP)
  @Column(name = "price_timestamp", nullable = false, columnDefinition = "datetime(6)")
  private Instant priceTimestamp;

  @Column(name = "source_name", nullable = false, length = 100)
  private String sourceName;
}
