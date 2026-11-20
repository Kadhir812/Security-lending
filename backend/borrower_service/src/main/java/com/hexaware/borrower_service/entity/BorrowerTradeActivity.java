package com.hexaware.borrower_service.entity;

import com.hexaware.borrower_service.enums.BorrowerTradeType;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "borrower_trade_activity")
public class BorrowerTradeActivity {
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "trade_id", nullable = false)
  private Long tradeId;

  @Column(name = "trade_reference", nullable = false, unique = true, length = 80)
  private String tradeReference;

  @Column(name = "transaction_reference", length = 80)
  private String transactionReference;

  @Column(name = "borrower_organization_reference", length = 80)
  private String borrowerOrganizationReference;

  @Column(name = "security_isin", length = 12)
  private String securityIsin;

  @Enumerated(EnumType.STRING)
  @Column(name = "trade_type", nullable = false, length = 20)
  private BorrowerTradeType tradeType;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Column(name = "price", nullable = false, precision = 20, scale = 6)
  private BigDecimal price;

  @Column(name = "realized_profit_loss", precision = 20, scale = 2)
  private BigDecimal realizedProfitLoss;

  @Column(name = "trade_time", nullable = false)
  private Instant tradeTime;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;
}
