package com.hexaware.borrower_service.entity;

import com.hexaware.borrower_service.enums.MessageLogDirection;
import com.hexaware.borrower_service.enums.MessageLogProcessingStatus;
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
    name = "message_log",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"service_name", "direction", "event_id"})
    },
    indexes = {
      @Index(name = "idx_message_log_processing", columnList = "correlation_id, created_at")
    })
public class MessageLog {

  @Column(name = "producer_service", nullable = false, length = 80)
  private String producerService;

  @Column(name = "destination_service", nullable = true, length = 80)
  private String destinationService;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "message_log_id", nullable = false)
  private Long messageLogId;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "event_id", nullable = false, length = 36, columnDefinition = "char(36)")
  private String eventId;

  @Column(name = "service_name", nullable = false, length = 80)
  private String serviceName;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, columnDefinition = "enum('INBOUND','OUTBOUND')")
  private MessageLogDirection direction;

  @Column(name = "correlation_id", nullable = false, length = 80)
  private String correlationId;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "causation_event_id", nullable = true, length = 36, columnDefinition = "char(36)")
  private String causationEventId;

  @Column(name = "sender_organization_reference", nullable = true, length = 80)
  private String senderOrganizationReference;

  @Column(name = "receiver_organization_reference", nullable = true, length = 80)
  private String receiverOrganizationReference;

  @Column(name = "application_message_type", nullable = false, length = 100)
  private String applicationMessageType;

  @Column(name = "fix_message_type", nullable = true, length = 8)
  private String fixMessageType;

  @Column(name = "fix_version", nullable = true, length = 20)
  private String fixVersion;

  @Column(name = "fix_session_id", nullable = true, length = 150)
  private String fixSessionId;

  @Column(name = "fix_sequence_number", nullable = true)
  private Long fixSequenceNumber;

  @Column(name = "raw_fix", nullable = true, columnDefinition = "longtext")
  private String rawFix;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "json")
  private Map<String, Object> payload;

  @Builder.Default
  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion = 1;

  @Column(name = "kafka_topic", nullable = true, length = 255)
  private String kafkaTopic;

  @Column(name = "kafka_partition", nullable = true)
  private Integer kafkaPartition;

  @Column(name = "kafka_offset", nullable = true)
  private Long kafkaOffset;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(
      name = "processing_status",
      nullable = false,
      columnDefinition = "enum('PENDING','PUBLISHED','PROCESSED','REJECTED','FAILED')")
  private MessageLogProcessingStatus processingStatus = MessageLogProcessingStatus.PENDING;

  @Column(name = "error_message", nullable = true, columnDefinition = "text")
  private String errorMessage;

  @Builder.Default
  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant createdAt = Instant.now();

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "processed_at", nullable = true, columnDefinition = "datetime(6)")
  private Instant processedAt;
}
