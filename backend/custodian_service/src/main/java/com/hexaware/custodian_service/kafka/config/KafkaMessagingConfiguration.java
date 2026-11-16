package com.hexaware.custodian_service.kafka.config;

import com.hexaware.custodian_service.exception.RetryableMessageException;
import com.hexaware.custodian_service.kafka.consumer.DeadLetterService;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableKafka
@EnableScheduling
@EnableConfigurationProperties(MessagingProperties.class)
public class KafkaMessagingConfiguration {
  private static final Logger log = LoggerFactory.getLogger(KafkaMessagingConfiguration.class);

  @Bean
  public ProducerFactory<String, String> messagingProducerFactory(MessagingProperties properties) {
    var config = new HashMap<String, Object>(properties.getClientProperties());
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, properties.getSendTimeoutMs());
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) properties.getSendTimeoutMs());
    config.put(
        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
        (int) Math.max(100, properties.getSendTimeoutMs() / 2));
    config.put(ProducerConfig.LINGER_MS_CONFIG, 0);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  public KafkaTemplate<String, String> messagingKafkaTemplate(
      ProducerFactory<String, String> factory) {
    return new KafkaTemplate<>(factory);
  }

  @Bean
  public ConsumerFactory<String, String> messagingConsumerFactory(MessagingProperties properties) {
    var config = new HashMap<String, Object>(properties.getClientProperties());
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    config.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
    config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
    return new DefaultKafkaConsumerFactory<>(config);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> messagingListenerFactory(
      ConsumerFactory<String, String> factory,
      DeadLetterService deadLetters,
      MessagingProperties properties) {
    var listener = new ConcurrentKafkaListenerContainerFactory<String, String>();
    listener.setConsumerFactory(factory);
    listener.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    listener.getContainerProperties().setSyncCommits(true);
    var backoff = new ExponentialBackOffWithMaxRetries(properties.getConsumerRetries());
    backoff.setInitialInterval(properties.getInitialBackoffMs());
    backoff.setMaxInterval(properties.getMaxBackoffMs());
    backoff.setMultiplier(2.0);
    var errors =
        new DefaultErrorHandler(
            (record, failure) -> {
              @SuppressWarnings("unchecked")
              var message =
                  (org.apache.kafka.clients.consumer.ConsumerRecord<String, String>) record;
              Throwable cause = rootCause(failure);
              log.error(
                  "Kafka message processing exhausted: topic={}, partition={}, offset={}, exception={}, message={}",
                  message.topic(),
                  message.partition(),
                  message.offset(),
                  cause.getClass().getName(),
                  cause.getMessage());
              deadLetters.reject(message, "PROCESSING_RETRY_EXHAUSTED");
            },
            backoff);
    errors.setClassifications(
        Map.of(
            TransientDataAccessException.class,
            true,
            RetryableMessageException.class,
            true,
            KafkaException.class,
            true),
        false);
    errors.setCommitRecovered(true);
    listener.setCommonErrorHandler(errors);
    return listener;
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }
}
