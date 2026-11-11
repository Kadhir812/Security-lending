package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.kafka.config.MessagingProperties;
import com.hexaware.custodian_service.kafka.event.EnvelopeCodec;
import com.hexaware.custodian_service.kafka.producer.EventTransport;
import com.hexaware.custodian_service.service.messaging.RejectionAuditService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeadLetterService {
  private final RejectionAuditService audit;
  private final EventTransport transport;
  private final EnvelopeCodec codec;
  private final MessagingProperties properties;

  public void reject(ConsumerRecord<String, String> record, String reason) {
    var evidence = audit.record(record, reason);
    if (properties.getDeadLetterTopic().isBlank())
      throw new IllegalStateException("Dead-letter topic required");
    transport.send(
        properties.getDeadLetterTopic(),
        (String) evidence.get("rejectionId"),
        codec.json(evidence));
    // Recovery only returns after audit commit AND a successful dead-letter broker acknowledgement.
  }
}
