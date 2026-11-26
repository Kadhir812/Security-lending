package com.hexaware.borrower_service.entity;

import com.hexaware.borrower_service.enums.LocateStatus;
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

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "locate_request")
public class LocateRequest {
  @Column(name = "borrower_organization_reference", nullable = false, length = 80)
  private String borrowerOrganizationReference;

  @Column(name = "custodian_organization_reference", nullable = false, length = 80)
  private String custodianOrganizationReference;

  @Column(name = "submission_status", nullable = false)
  private String submissionStatus;

  @Column(name = "decision_source_version", nullable = false)
  private Long decisionSourceVersion;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "locate_id", nullable = false)
  private Long locateId;

  @Column(name = "locate_reference", nullable = false, unique = true, length = 80)
  private String locateReference;

  @Column(name = "msla_reference", nullable = false, length = 80)
  private String mslaReference;

  @Column(name = "security_isin", nullable = false, length = 12, columnDefinition = "char(12)")
  private String securityIsin;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Column(name = "approved_quantity")
  private Long approvedQuantity;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Column(name = "request_time", nullable = false, columnDefinition = "datetime(6)")
  private Instant requestTime;

  @Column(name = "expiry_time", columnDefinition = "datetime(6)")
  private Instant expiryTime;

  @Column(name = "responded_at", columnDefinition = "datetime(6)")
  private Instant respondedAt;

  @Column(name = "response_reason", length = 500)
  private String responseReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private LocateStatus status;
}
