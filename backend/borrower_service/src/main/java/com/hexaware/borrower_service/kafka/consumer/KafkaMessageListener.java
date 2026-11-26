package com.hexaware.borrower_service.kafka.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.messaging.consumer-enabled", havingValue = "true")
public class KafkaMessageListener {
  private final MessageReceiver receiver;

  @KafkaListener(
      id = "messaging",
      topics = "${app.messaging.inbound-topic}",
      groupId = "${app.messaging.group-id}",
      containerFactory = "messagingListenerFactory")
  public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
    receiver.receive(record, acknowledgment);
  }
}
