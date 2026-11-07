package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.MandateStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "lender_custodian_mandate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LenderMandate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "mandate_id")
  private Long mandateId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "mandate_reference", nullable = false, unique = true, length = 80)
  private String mandateReference;

  @Column(name = "lender_org_id", nullable = false)
  private Long lenderOrgId;

  @Column(name = "custodian_org_id", nullable = false)
  private Long custodianOrgId;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "status",
      nullable = false,
      columnDefinition = "enum('ACTIVE','INACTIVE','EXPIRED')")
  private MandateStatus status;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", columnDefinition = "datetime(6)")
  private Instant createdAt;

  @Column(name = "created_by_user_id")
  private Long createdByUserId;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "updated_at", columnDefinition = "datetime(6)")
  private Instant updatedAt;

  @Column(name = "updated_by_user_id")
  private Long updatedByUserId;
}
