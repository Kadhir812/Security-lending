package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
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

/** Service-local mapping of the supplied SQL; Hibernate only validates the schema. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "organization")
public class Organization {

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "organization_reference", nullable = false, unique = true, length = 80)
  private String organizationReference;

  @Column(name = "organization_name", nullable = false, length = 255)
  private String organizationName;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "organization_type",
      nullable = false,
      columnDefinition = "enum('LENDER','BORROWER','CUSTODIAN')")
  private OrganizationType organizationType;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "enum('ACTIVE','INACTIVE')")
  private OrganizationStatus status = OrganizationStatus.ACTIVE;

  @Builder.Default
  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant createdAt = Instant.now();

  @Column(name = "registered_by_custodian_id")
  private Long registeredByCustodianId;

  @Column(name = "created_by_user_id")
  private Long createdByUserId;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "updated_at", columnDefinition = "datetime(6)")
  private Instant updatedAt;

  @Column(name = "updated_by_user_id")
  private Long updatedByUserId;
}
