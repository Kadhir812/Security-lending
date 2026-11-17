package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.InventoryReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "inventory_reservation")
public class InventoryReservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "reservation_id", nullable = false)
  private Long reservationId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "contract_id", nullable = false, unique = true)
  private Long contractId;

  @Column(name = "inventory_id", nullable = false)
  private Long inventoryId;

  @Column(name = "reserved_quantity", nullable = false)
  private Long reservedQuantity;

  @Builder.Default
  @Column(name = "consumed_quantity", nullable = false)
  private Long consumedQuantity = 0L;

  @Builder.Default
  @Column(name = "released_quantity", nullable = false)
  private Long releasedQuantity = 0L;

  @Column(name = "remaining_quantity", insertable = false, updatable = false)
  private Long remainingQuantity;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private InventoryReservationStatus status = InventoryReservationStatus.ACTIVE;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant createdAt;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "expires_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant expiresAt;
}
