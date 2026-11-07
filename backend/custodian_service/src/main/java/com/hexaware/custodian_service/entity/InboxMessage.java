package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.InboxMessageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
    name = "inbox_message",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"consumer_name", "event_id"})},
    indexes = {@Index(name = "idx_inbox_message_processing", columnList = "correlation_id")})
@Check(constraints = "(kafka_partition >= 0 AND kafka_offset >= 0)")
public class InboxMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "inbox_id", nullable = false)
  private Long inboxId;

  @Column(name = "consumer_name", nullable = false, length = 150)
  private String consumerName;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "event_id", nullable = false, length = 36, columnDefinition = "char(36)")
  private String eventId;

  @Column(name = "correlation_id", nullable = false, length = 80)
  private String correlationId;

  @Column(name = "aggregate_reference", nullable = false, length = 80)
  private String aggregateReference;

  @Column(name = "aggregate_version", nullable = false)
  private Long aggregateVersion;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "json")
  private Map<String, Object> payload;

  @Column(name = "kafka_topic", nullable = false, length = 255)
  private String kafkaTopic;

  @Column(name = "kafka_partition", nullable = false)
  private Integer kafkaPartition;

  @Column(name = "kafka_offset", nullable = false)
  private Long kafkaOffset;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(
      name = "status",
      nullable = false,
      columnDefinition = "enum('RECEIVED','PROCESSED','REJECTED')")
  private InboxMessageStatus status = InboxMessageStatus.RECEIVED;

  @Builder.Default
  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "received_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant receivedAt = Instant.now();

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "processed_at", nullable = true, columnDefinition = "datetime(6)")
  private Instant processedAt;

  @Column(name = "error_message", nullable = true, columnDefinition = "text")
  private String errorMessage;
}
