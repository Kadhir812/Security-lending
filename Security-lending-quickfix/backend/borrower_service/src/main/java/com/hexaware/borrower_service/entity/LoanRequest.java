package com.hexaware.borrower_service.entity;

import com.hexaware.borrower_service.enums.LoanRequestStatus;
import com.hexaware.borrower_service.enums.SubmissionStatus;
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
import java.time.LocalDate;
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
@Table(name = "loan_request")
public class LoanRequest {
  @Column(name = "borrower_organization_reference", nullable = false, length = 80)
  private String borrowerOrganizationReference;

  @Column(name = "custodian_organization_reference", nullable = false, length = 80)
  private String custodianOrganizationReference;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "submission_status", nullable = false)
  private SubmissionStatus submissionStatus = SubmissionStatus.QUEUED;

  @Column(name = "decision_source_version", nullable = false)
  private Long decisionSourceVersion;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "request_id", nullable = false)
  private Long requestId;

  @Column(name = "request_reference", nullable = false, unique = true, length = 80)
  private String requestReference;

  @Column(name = "msla_reference", nullable = false, length = 80)
  private String mslaReference;

  @Column(name = "locate_id", unique = true)
  private Long locateId;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "security_isin", nullable = false, length = 12, columnDefinition = "char(12)")
  private String securityIsin;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Column(name = "requested_settlement_date", nullable = false)
  private LocalDate requestedSettlementDate;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "settlement_currency", length = 3, columnDefinition = "char(3)")
  private String settlementCurrency;

  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @Builder.Default
  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "requested_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant requestedAt = Instant.now();

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private LoanRequestStatus status = LoanRequestStatus.PENDING;

  @Column(name = "response_reason", length = 500)
  private String responseReason;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "responded_at", columnDefinition = "datetime(6)")
  private Instant respondedAt;
}
