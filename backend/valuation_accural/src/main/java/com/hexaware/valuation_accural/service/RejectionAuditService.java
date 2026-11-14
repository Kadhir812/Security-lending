package com.hexaware.valuation_accural.service;

import com.hexaware.valuation_accural.kafka.config.MessagingProperties;
import com.hexaware.valuation_accural.kafka.event.EnvelopeCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RejectionAuditService {
  private final JdbcTemplate jdbc;
  private final MessagingProperties properties;
  private final EnvelopeCodec codec;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Map<String, Object> record(ConsumerRecord<String, String> record, String reason) {
    String identity =
        properties.getConsumerName()
            + ":"
            + record.topic()
            + ":"
            + record.partition()
            + ":"
            + record.offset();
    String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    Map<String, Object> evidence =
        Map.of(
            "rejectionId",
            id,
            "reason",
            reason,
            "consumerName",
            properties.getConsumerName(),
            "originalTopic",
            record.topic(),
            "originalPartition",
            record.partition(),
            "originalOffset",
            record.offset(),
            "originalSha256",
            digest(record.value()));
    // Untrusted raw bodies/headers may contain credentials. Retain only location and digest.
    jdbc.update(
        """
        INSERT INTO message_log (producer_service, destination_service, event_id, service_name, direction,
            correlation_id, application_message_type, payload, kafka_topic, kafka_partition, kafka_offset,
            processing_status, error_message, processed_at)
        VALUES ('UNTRUSTED', ?, ?, ?, 'INBOUND', ?, 'MESSAGE_REJECTED', CAST(? AS JSON), ?, ?, ?, 'REJECTED', ?, UTC_TIMESTAMP(6))
        ON DUPLICATE KEY UPDATE error_message=VALUES(error_message)
        """,
        properties.getServiceName(),
        id,
        properties.getServiceName(),
        id,
        codec.json(evidence),
        record.topic(),
        record.partition(),
        record.offset(),
        reason);
    return evidence;
  }

  private String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
