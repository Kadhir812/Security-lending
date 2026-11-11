package com.hexaware.custodian_service.kafka.outbox;

import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.enums.MessageLogDirection;
import com.hexaware.custodian_service.enums.MessageLogProcessingStatus;
import com.hexaware.custodian_service.kafka.config.MessagingProperties;
import com.hexaware.custodian_service.kafka.producer.DeliveryReceipt;
import com.hexaware.custodian_service.repository.MessageLogRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OutboxStore {
  private final JdbcTemplate jdbc;
  private final OutboxEventRepository repository;
  private final MessageLogRepository messages;
  private final MessagingProperties properties;
  private final RetryPolicy retries;
  private final Clock clock;

  /** Short database-only transaction. Never keep row locks while waiting for Kafka. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<OutboxEvent> claim() {
    var candidates =
        jdbc.query(
            """
            SELECT current.outbox_id, current.attempt_count FROM outbox_event current
            WHERE current.service_name = ? AND (
                (current.status = 'PENDING' AND current.next_attempt_at <= UTC_TIMESTAMP(6)) OR
                (current.status = 'PUBLISHING' AND current.locked_until <= UTC_TIMESTAMP(6)))
            AND NOT EXISTS (SELECT 1 FROM outbox_event older
                WHERE older.service_name = current.service_name AND older.partition_key = current.partition_key
                  AND older.outbox_id < current.outbox_id AND older.status <> 'PUBLISHED')
            ORDER BY current.outbox_id LIMIT 1 FOR UPDATE SKIP LOCKED
            """,
            (rs, row) ->
                OutboxCandidate.builder().id(rs.getLong(1)).attemptCount(rs.getInt(2)).build(),
            properties.getServiceName());
    if (candidates.isEmpty()) return Optional.empty();
    OutboxCandidate candidate = candidates.get(0);
    if (candidate.getAttemptCount() >= properties.getMaxAttempts()) {
      jdbc.update(
          "UPDATE outbox_event SET status='FAILED', locked_by=NULL, locked_until=NULL,"
              + " last_error='LEASE_RETRIES_EXHAUSTED' WHERE outbox_id=?",
          candidate.getId());
      OutboxEvent row = repository.findById(candidate.getId()).orElseThrow();
      updateAudit(row, MessageLogProcessingStatus.FAILED, "LEASE_RETRIES_EXHAUSTED", null);
      return Optional.empty();
    }
    jdbc.update(
        """
        UPDATE outbox_event SET status='PUBLISHING', attempt_count=attempt_count+1,
            locked_by=?, locked_until=TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6)) WHERE outbox_id=?
        """,
        UUID.randomUUID().toString(),
        properties.getLeaseMs() * 1000,
        candidate.getId());
    return repository.findById(candidate.getId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean published(OutboxEvent claim, DeliveryReceipt receipt) {
    int updated =
        jdbc.update(
            """
            UPDATE outbox_event SET status='PUBLISHED', published_at=UTC_TIMESTAMP(6),
                locked_by=NULL, locked_until=NULL, last_error=NULL
            WHERE outbox_id=? AND status='PUBLISHING' AND locked_by=? AND locked_until > UTC_TIMESTAMP(6)
            """,
            claim.getOutboxId(),
            claim.getLockedBy());
    if (updated == 1) updateAudit(claim, MessageLogProcessingStatus.PUBLISHED, null, receipt);
    return updated == 1;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(OutboxEvent claim, RuntimeException failure) {
    boolean retry =
        retries.retryable(failure) && claim.getAttemptCount() < properties.getMaxAttempts();
    String error = retries.safeFailure(failure);
    int updated =
        jdbc.update(
            """
            UPDATE outbox_event SET status=?, next_attempt_at=TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6)),
                locked_by=NULL, locked_until=NULL, last_error=?
            WHERE outbox_id=? AND status='PUBLISHING' AND locked_by=? AND locked_until > UTC_TIMESTAMP(6)
            """,
            retry ? "PENDING" : "FAILED",
            retries.delayMs(claim.getAttemptCount()) * 1000,
            error,
            claim.getOutboxId(),
            claim.getLockedBy());
    if (updated == 1) updateAudit(claim, MessageLogProcessingStatus.FAILED, error, null);
  }

  private void updateAudit(
      OutboxEvent event, MessageLogProcessingStatus status, String error, DeliveryReceipt receipt) {
    messages
        .findByServiceNameAndDirectionAndEventId(
            properties.getServiceName(), MessageLogDirection.OUTBOUND, event.getEventId())
        .ifPresent(
            audit -> {
              audit.setProcessingStatus(status);
              audit.setErrorMessage(error);
              audit.setProcessedAt(clock.instant());
              if (receipt != null) {
                audit.setKafkaPartition(receipt.getPartition());
                audit.setKafkaOffset(receipt.getOffset());
              }
            });
  }
}
