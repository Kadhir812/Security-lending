package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "locate_request")
public class LocateRequest {
  @Column(name = "submitted_by_issuer", nullable = false, length = 255)
  private String submittedByIssuer;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "locate_id", nullable = false)
  private Long locateId;

  @Column(name = "locate_reference", nullable = false, unique = true, length = 80)
  private String locateReference;

  @Column(name = "msla_id", nullable = false)
  private Long mslaId;

  @Column(name = "security_isin", nullable = false, length = 12, columnDefinition = "char(12)")
  private String securityIsin;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Column(name = "approved_quantity")
  private Long approvedQuantity;

  @Column(name = "submitted_by_subject", nullable = false, length = 255)
  private String submittedBySubject;

  @Column(name = "request_time", nullable = false, columnDefinition = "datetime(6)")
  private Instant requestTime;

  @Column(name = "expiry_time", nullable = false, columnDefinition = "datetime(6)")
  private Instant expiryTime;

  @Column(name = "status", nullable = false)
  private String status;
}
