package com.hexaware.valuation_accural.kafka.outbox;

import com.hexaware.valuation_accural.kafka.config.MessagingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.messaging.publisher-enabled", havingValue = "true")
public class OutboxPoller {
  private final OutboxPublisher publisher;
  private final MessagingProperties properties;

  @Scheduled(fixedDelayString = "${app.messaging.poll-interval-ms:1000}")
  public void poll() {
    try {
      for (int i = 0;
          i < properties.getBatchSize() && !Thread.currentThread().isInterrupted();
          i++) {
        if (!publisher.publishOne()) break;
      }
    } catch (RuntimeException failure) {
      log.warn("Outbox poll deferred: {}", failure.getClass().getSimpleName());
    }
  }
}
