package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.CollateralType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "collateral")
public class Collateral {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "collateral_id")
  private Long collateralId;
  @Version @Column(nullable = false)
  private Long version;
  @Column(name = "collateral_reference", nullable = false, unique = true, length = 80)
  private String collateralReference;
  @Column(name = "contract_id", nullable = false)
  private Long contractId;
  @Enumerated(EnumType.STRING)
  @Column(name = "collateral_type", nullable = false)
  private CollateralType collateralType;
  @Column(length = 12)
  private String securityIsin;
  @Column(nullable = false, length = 3)
  private String currency;
  @Column(name = "pledged_quantity", precision = 24, scale = 6)
  private BigDecimal pledgedQuantity;
  @Builder.Default @Column(name = "released_quantity", nullable = false, precision = 24, scale = 6)
  private BigDecimal releasedQuantity = BigDecimal.ZERO;
  @Column(name = "posted_cash_amount", precision = 20, scale = 2)
  private BigDecimal postedCashAmount;
  @Builder.Default @Column(name = "released_cash_amount", nullable = false, precision = 20, scale = 2)
  private BigDecimal releasedCashAmount = BigDecimal.ZERO;
  @Builder.Default @Column(name = "applied_haircut_pct", nullable = false, precision = 9, scale = 4)
  private BigDecimal appliedHaircutPct = BigDecimal.ZERO;
  @Column(name = "posted_at")
  private Instant postedAt;
  @Column(nullable = false)
  private String status;
}
