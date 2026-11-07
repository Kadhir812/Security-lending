package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.kafka.inbox.InboxProcessor;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageReceiver {
  private final InboxProcessor inbox;
  private final DeadLetterService deadLetters;

  public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
    try {
      inbox.process(record);
    } catch (InvalidEnvelopeException invalid) {
      deadLetters.reject(record, invalid.getReason());
    }
    // The proxied inbox method has committed before it returns, including duplicate detection.
    acknowledgment.acknowledge();
  }
}
