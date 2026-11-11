package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.kafka.inbox.InboxProcessor;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@RequiredArgsConstructor
public class MessageReceiver {
  private static final Logger log = LoggerFactory.getLogger(MessageReceiver.class);

  private final InboxProcessor inbox;
  private final DeadLetterService deadLetters;

  public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
    try {
      inbox.process(record);
    } catch (InvalidEnvelopeException invalid) {
      log.warn(
          "Kafka message rejected: topic={}, partition={}, offset={}, reason={}",
          record.topic(),
          record.partition(),
          record.offset(),
          invalid.getReason());
      deadLetters.reject(record, invalid.getReason());
    }
    // The proxied inbox method has committed before it returns, including duplicate detection.
    acknowledgment.acknowledge();
  }
}
