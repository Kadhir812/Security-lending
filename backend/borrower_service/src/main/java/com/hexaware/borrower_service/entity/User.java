package com.hexaware.borrower_service.entity;

import com.hexaware.borrower_service.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"identity_issuer", "identity_subject"}),
      @UniqueConstraint(columnNames = {"organization_reference", "username"})
    })
public class User {

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "identity_issuer", nullable = false, length = 255)
  private String identityIssuer;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "organization_reference", nullable = false, length = 80)
  private String organizationReference;

  @Column(name = "organization_name", length = 255)
  private String organizationName;

  @Column(name = "identity_subject", nullable = false, length = 255)
  private String identitySubject;

  @Column(name = "username", nullable = false, length = 100)
  private String username;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = true, length = 100)
  private String passwordHash;

  @Column(name = "phone", nullable = true, length = 50)
  private String phone;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, columnDefinition = "enum('ACTIVE','INACTIVE')")
  private UserStatus status = UserStatus.ACTIVE;

  @Builder.Default
  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant createdAt = Instant.now();
}
