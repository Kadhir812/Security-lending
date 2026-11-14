package com.hexaware.valuation_accural.kafka.producer;

import com.hexaware.valuation_accural.kafka.config.MessagingProperties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventTransport implements EventTransport {
  private final KafkaTemplate<String, String> template;
  private final MessagingProperties properties;

  @Override
  public DeliveryReceipt send(String topic, String key, String json) {
    try {
      var result =
          template.send(topic, key, json).get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
      var metadata = result.getRecordMetadata();
      return DeliveryReceipt.builder()
          .partition(metadata.partition())
          .offset(metadata.offset())
          .build();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new KafkaException("DELIVERY_INTERRUPTED", exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new KafkaException("DELIVERY_FAILED", exception);
    }
  }
}
