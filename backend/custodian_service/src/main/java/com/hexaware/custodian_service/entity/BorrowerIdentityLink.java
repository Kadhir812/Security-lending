package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "borrower_identity_link")
public class BorrowerIdentityLink {

  @Id
  @Column(name = "jti", nullable = false, length = 36)
  private String jti;

  @Column(name = "organization_reference", nullable = false, length = 80)
  private String organizationReference;

  @Column(name = "consumed_by_custodian_id", nullable = false)
  private Long consumedByCustodianId;

  @Column(name = "consumed_by_user_id", nullable = false)
  private Long consumedByUserId;

  @Builder.Default
  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "consumed_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant consumedAt = Instant.now();
}
