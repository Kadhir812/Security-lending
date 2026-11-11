package com.hexaware.borrower_service.service.messaging;

import com.hexaware.borrower_service.entity.MessageLog;
import com.hexaware.borrower_service.enums.MessageLogDirection;
import com.hexaware.borrower_service.enums.MessageLogProcessingStatus;
import com.hexaware.borrower_service.kafka.config.MessagingProperties;
import com.hexaware.borrower_service.kafka.event.EnvelopeCodec;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.MessageLogRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageAuditService {
  private final MessageLogRepository repository;
  private final MessagingProperties properties;
  private final EnvelopeCodec codec;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public MessageLog record(
      EventEnvelope event,
      MessageLogDirection direction,
      MessageLogProcessingStatus status,
      String topic,
      Integer partition,
      Long offset) {
    MessageLog audit =
        repository
            .findByServiceNameAndDirectionAndEventId(
                properties.getServiceName(), direction, event.getEventId())
            .orElseGet(
                () ->
                    MessageLog.builder()
                        .serviceName(properties.getServiceName())
                        .eventId(event.getEventId())
                        .direction(direction)
                        .createdAt(clock.instant())
                        .build());
    audit.setProducerService(event.getProducerService());
    audit.setDestinationService(event.getDestinationService());
    audit.setCorrelationId(event.getCorrelationId());
    audit.setCausationEventId(event.getCausationEventId());
    audit.setSenderOrganizationReference(event.getSenderOrganizationReference());
    audit.setReceiverOrganizationReference(event.getReceiverOrganizationReference());
    audit.setApplicationMessageType(event.getEventType());
    audit.setSchemaVersion(event.getSchemaVersion());
    audit.setPayload(codec.toMap(event));
    audit.setKafkaTopic(topic);
    audit.setKafkaPartition(partition);
    audit.setKafkaOffset(offset);
    audit.setProcessingStatus(status);
    if (status != MessageLogProcessingStatus.PENDING) audit.setProcessedAt(clock.instant());
    return repository.save(audit);
  }
}
