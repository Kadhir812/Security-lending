package com.hexaware.valuation_accural.entity;

import com.hexaware.valuation_accural.enums.OutboxEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Service-local mapping of the supplied SQL; Hibernate only validates the schema. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(
    name = "outbox_event",
    indexes = {
      @Index(name = "idx_outbox_event_processing", columnList = "status, next_attempt_at")
    })
@Check(constraints = "(attempt_count >= 0 AND aggregate_version >= 0)")
public class OutboxEvent {

  @Column(name = "producer_service", nullable = false, length = 80)
  private String producerService;

  @Column(name = "destination_service", nullable = true, length = 80)
  private String destinationService;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "outbox_id", nullable = false)
  private Long outboxId;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(
      name = "event_id",
      nullable = false,
      unique = true,
      length = 36,
      columnDefinition = "char(36)")
  private String eventId;

  @Column(name = "service_name", nullable = false, length = 80)
  private String serviceName;

  @Column(name = "aggregate_type", nullable = false, length = 80)
  private String aggregateType;

  @Column(name = "aggregate_reference", nullable = false, length = 80)
  private String aggregateReference;

  @Column(name = "aggregate_version", nullable = false)
  private Long aggregateVersion;

  @Column(name = "correlation_id", nullable = false, length = 80)
  private String correlationId;

  @Column(name = "sender_organization_reference", nullable = true, length = 80)
  private String senderOrganizationReference;

  @Column(name = "receiver_organization_reference", nullable = true, length = 80)
  private String receiverOrganizationReference;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Builder.Default
  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion = 1;

  @Column(name = "kafka_topic", nullable = false, length = 255)
  private String kafkaTopic;

  @Column(name = "partition_key", nullable = false, length = 150)
  private String partitionKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "json")
  private Map<String, Object> payload;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(
      name = "status",
      nullable = false,
      columnDefinition = "enum('PENDING','PUBLISHING','PUBLISHED','FAILED')")
  private OutboxEventStatus status = OutboxEventStatus.PENDING;

  @Builder.Default
  @Column(name = "attempt_count", nullable = false)
  private Integer attemptCount = 0;

  @Builder.Default
  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "next_attempt_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant nextAttemptAt = Instant.now();

  @Column(name = "locked_by", nullable = true, length = 150)
  private String lockedBy;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "locked_until", nullable = true, columnDefinition = "datetime(6)")
  private Instant lockedUntil;

  @Builder.Default
  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant createdAt = Instant.now();

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "published_at", nullable = true, columnDefinition = "datetime(6)")
  private Instant publishedAt;

  @Column(name = "last_error", nullable = true, columnDefinition = "text")
  private String lastError;
}
