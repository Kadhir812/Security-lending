package com.hexaware.custodian_service.service.messaging;

import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.enums.MessageLogDirection;
import com.hexaware.custodian_service.enums.MessageLogProcessingStatus;
import com.hexaware.custodian_service.kafka.config.MessagingProperties;
import com.hexaware.custodian_service.kafka.event.EnvelopeCodec;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.kafka.event.EventRouting;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class OutgoingEventService {
  private final OutboxEventRepository repository;
  private final EnvelopeCodec codec;
  private final EventRouting routing;
  private final MessagingProperties properties;
  private final MessageAuditService audit;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public OutboxEvent enqueue(String aggregateType, EventEnvelope event) {
    if (TransactionSynchronizationManager.isCurrentTransactionReadOnly())
      throw new IllegalStateException("Writable transaction required");
    if (aggregateType == null || aggregateType.isBlank() || aggregateType.length() > 80)
      throw new IllegalArgumentException("Invalid aggregate type");
    codec.validate(event);
    String topic = routing.outgoingTopic(event);
    OutboxEvent row =
        OutboxEvent.builder()
            .eventId(event.getEventId())
            .producerService(event.getProducerService())
            .destinationService(event.getDestinationService())
            .serviceName(properties.getServiceName())
            .aggregateType(aggregateType)
            .aggregateReference(event.getAggregateReference())
            .aggregateVersion(event.getAggregateVersion())
            .correlationId(event.getCorrelationId())
            .senderOrganizationReference(event.getSenderOrganizationReference())
            .receiverOrganizationReference(event.getReceiverOrganizationReference())
            .eventType(event.getEventType())
            .schemaVersion(event.getSchemaVersion())
            .kafkaTopic(topic)
            .partitionKey(event.getAggregateReference())
            .payload(codec.toMap(event))
            .createdAt(clock.instant())
            .nextAttemptAt(clock.instant())
            .build();
    repository.save(row);
    audit.record(
        event, MessageLogDirection.OUTBOUND, MessageLogProcessingStatus.PENDING, topic, null, null);
    return row;
  }
}
