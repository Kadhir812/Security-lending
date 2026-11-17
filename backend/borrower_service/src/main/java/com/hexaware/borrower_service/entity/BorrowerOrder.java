package com.hexaware.borrower_service.entity;

import com.hexaware.borrower_service.enums.BorrowerOrderSide;
import com.hexaware.borrower_service.enums.BorrowerOrderStatus;
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
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "borrower_order")
public class BorrowerOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "order_reference", nullable = false, unique = true, length = 80)
  private String orderReference;

  @Column(name = "borrower_organization_reference", nullable = false, length = 80)
  private String borrowerOrganizationReference;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "security_isin", nullable = false, length = 12, columnDefinition = "char(12)")
  private String securityIsin;

  @Column(name = "transaction_reference", length = 80)
  private String transactionReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "side", nullable = false)
  private BorrowerOrderSide side;

  @Column(name = "order_quantity", nullable = false)
  private Long orderQuantity;

  @Column(name = "filled_quantity", nullable = false)
  private Long filledQuantity;

  @Column(name = "leaves_quantity", nullable = false)
  private Long leavesQuantity;

  @Column(name = "average_fill_price", precision = 20, scale = 6)
  private BigDecimal averageFillPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_status", nullable = false)
  private BorrowerOrderStatus orderStatus;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant createdAt;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant updatedAt;
}
