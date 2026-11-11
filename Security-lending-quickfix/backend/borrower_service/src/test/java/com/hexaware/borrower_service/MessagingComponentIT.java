package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.entity.OutboxEvent;
import com.hexaware.borrower_service.exception.RetryableMessageException;
import com.hexaware.borrower_service.kafka.config.EventRoute;
import com.hexaware.borrower_service.kafka.config.MessagingProperties;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.consumer.MessageReceiver;
import com.hexaware.borrower_service.kafka.event.EnvelopeCodec;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.kafka.event.EventRouting;
import com.hexaware.borrower_service.kafka.inbox.InboxProcessor;
import com.hexaware.borrower_service.kafka.outbox.OutboxPublisher;
import com.hexaware.borrower_service.kafka.outbox.OutboxStore;
import com.hexaware.borrower_service.kafka.outbox.RetryPolicy;
import com.hexaware.borrower_service.kafka.producer.DeliveryReceipt;
import com.hexaware.borrower_service.kafka.producer.EventTransport;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = {BorrowerServiceApplication.class, MessagingComponentIT.Fixtures.class})
class MessagingComponentIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws Exception {
    PersistenceIT.database(registry);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fixtures {
    @Bean
    @Primary
    EventTransport testTransport() {
      return mock(EventTransport.class);
    }

    @Bean
    TestHandler testHandler(OutgoingEventService outgoing, MessagingProperties properties) {
      return new TestHandler(outgoing, properties);
    }
  }

  static class TestHandler implements MessageHandler {
    private final OutgoingEventService outgoing;
    private final MessagingProperties properties;
    final AtomicInteger invocations = new AtomicInteger();
    final AtomicBoolean fail = new AtomicBoolean();

    TestHandler(OutgoingEventService outgoing, MessagingProperties properties) {
      this.outgoing = outgoing;
      this.properties = properties;
    }

    @Override
    public String eventType() {
      return "COMPONENT_INPUT";
    }

    @Override
    public void handle(EventEnvelope input) {
      invocations.incrementAndGet();
      outgoing.enqueue(
          "STAGE2_COMPONENT",
          EventEnvelope.builder()
              .eventId(
                  UUID.nameUUIDFromBytes(
                          (input.getEventId() + ":response")
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                      .toString())
              .eventType("COMPONENT_OUTPUT")
              .schemaVersion(1)
              .producerService(properties.getServiceName())
              .destinationService("PEER")
              .correlationId(input.getCorrelationId())
              .causationEventId(input.getEventId())
              .aggregateReference(input.getAggregateReference())
              .aggregateVersion(input.getAggregateVersion())
              .occurredAt(input.getOccurredAt())
              .payload(Map.of("accepted", true))
              .build());
      if (fail.get()) throw new RetryableMessageException();
    }
  }

  @Autowired MessagingProperties properties;
  @Autowired EnvelopeCodec codec;
  @Autowired EventRouting routing;
  @Autowired OutgoingEventService outgoing;
  @Autowired OutboxStore store;
  @Autowired OutboxPublisher publisher;
  @Autowired InboxProcessor inbox;
  @Autowired MessageReceiver receiver;
  @Autowired EventTransport transport;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired TestHandler handler;
  TransactionTemplate transaction;
  private final AtomicLong offset = new AtomicLong();

  @BeforeEach
  void setup() {
    transaction = new TransactionTemplate(manager);
    properties.setServiceName(
        "SERVICE_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
    properties.setConsumerName(properties.getServiceName().toLowerCase(Locale.ROOT));
    properties.setDeadLetterTopic("component-in.DLT");
    properties.setMaxAttempts(3);
    properties.setInitialBackoffMs(10);
    properties.setMaxBackoffMs(40);
    properties.setRoutes(
        List.of(
            EventRoute.builder()
                .eventType("COMPONENT_INPUT")
                .producerService("PEER")
                .destinationService(properties.getServiceName())
                .topic("component-in")
                .build(),
            EventRoute.builder()
                .eventType("COMPONENT_OUTPUT")
                .producerService(properties.getServiceName())
                .destinationService("PEER")
                .topic("component-out")
                .build()));
    handler.invocations.set(0);
    handler.fail.set(false);
    reset(transport);
    when(transport.send(anyString(), anyString(), anyString()))
        .thenReturn(DeliveryReceipt.builder().partition(0).offset(10).build());
  }

  EventEnvelope input() {
    return EventEnvelope.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("COMPONENT_INPUT")
        .schemaVersion(1)
        .producerService("PEER")
        .destinationService(properties.getServiceName())
        .correlationId(UUID.randomUUID().toString())
        .aggregateReference("test-" + UUID.randomUUID())
        .aggregateVersion(1L)
        .occurredAt(Instant.now())
        .payload(Map.of("probe", true))
        .build();
  }

  ConsumerRecord<String, String> record(EventEnvelope event) {
    return new ConsumerRecord<>(
        "component-in",
        0,
        offset.incrementAndGet(),
        event.getAggregateReference(),
        codec.encode(event));
  }

  OutboxEvent enqueue() {
    var event = input();
    event.setEventType("COMPONENT_OUTPUT");
    event.setProducerService(properties.getServiceName());
    event.setDestinationService("PEER");
    return transaction.execute(status -> outgoing.enqueue("STAGE2_COMPONENT", event));
  }

  long count(String table) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM "
            + table
            + " WHERE "
            + (table.equals("inbox_message") ? "consumer_name" : "service_name")
            + "=?",
        Long.class,
        table.equals("inbox_message") ? properties.getConsumerName() : properties.getServiceName());
  }

  String status(OutboxEvent event) {
    return jdbc.queryForObject(
        "SELECT status FROM outbox_event WHERE outbox_id=?", String.class, event.getOutboxId());
  }

  void expire(OutboxEvent event) {
    jdbc.update(
        "UPDATE outbox_event SET locked_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE"
            + " outbox_id=?",
        event.getOutboxId());
  }

  void due(OutboxEvent event) {
    jdbc.update(
        "UPDATE outbox_event SET next_attempt_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE"
            + " outbox_id=?",
        event.getOutboxId());
  }

  @Test
  void enqueueRequiresAndRollsBackWithCallerTransaction() {
    var event = input();
    assertThatThrownBy(() -> outgoing.enqueue("STAGE2_COMPONENT", event))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    assertThatThrownBy(
            () ->
                transaction.execute(
                    status -> {
                      enqueue();
                      throw new IllegalStateException("ROLLBACK");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("outbox_event")).isZero();
    assertThat(count("message_log")).isZero();
  }

  @Test
  void publishesOnlyAfterAcknowledgementAndUpdatesAudit() {
    var event = enqueue();
    assertThat(status(event)).isEqualTo("PENDING");
    assertThat(publisher.publishOne()).isTrue();
    assertThat(status(event)).isEqualTo("PUBLISHED");
    assertThat(
            jdbc.queryForObject(
                "SELECT processing_status FROM message_log WHERE service_name=? AND event_id=?",
                String.class,
                properties.getServiceName(),
                event.getEventId()))
        .isEqualTo("PUBLISHED");
    verify(transport).send(eq("component-out"), eq(event.getPartitionKey()), anyString());
  }

  @Test
  void unavailableKafkaRetriesWithBackoffThenStopsAtBound() {
    var event = enqueue();
    when(transport.send(anyString(), anyString(), anyString()))
        .thenThrow(new KafkaException("UNAVAILABLE", new TimeoutException()));
    publisher.publishOne();
    assertThat(status(event)).isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT attempt_count FROM outbox_event WHERE outbox_id=?",
                Integer.class,
                event.getOutboxId()))
        .isEqualTo(1);
    due(event);
    publisher.publishOne();
    due(event);
    publisher.publishOne();
    assertThat(status(event)).isEqualTo("FAILED");
    assertThat(publisher.publishOne()).isFalse();
    assertThat(new RetryPolicy(properties).delayMs(100)).isEqualTo(40);
  }

  @Test
  void permanentProducerFailureIsNotRetried() {
    var event = enqueue();
    when(transport.send(anyString(), anyString(), anyString()))
        .thenThrow(new org.apache.kafka.common.errors.AuthorizationException("DENIED"));
    publisher.publishOne();
    assertThat(status(event)).isEqualTo("FAILED");
  }

  @Test
  void expiredLeaseRecoversAndOldOwnerCannotComplete() {
    var event = enqueue();
    var oldClaim = store.claim().orElseThrow();
    expire(event);
    var newClaim = store.claim().orElseThrow();
    assertThat(newClaim.getLockedBy()).isNotEqualTo(oldClaim.getLockedBy());
    assertThat(store.published(oldClaim, DeliveryReceipt.builder().partition(0).offset(1).build()))
        .isFalse();
    assertThat(status(event)).isEqualTo("PUBLISHING");
    assertThat(store.published(newClaim, DeliveryReceipt.builder().partition(0).offset(2).build()))
        .isTrue();
  }

  @Test
  void crashAfterSendBeforePublishedCausesSafeRedelivery() {
    var event = enqueue();
    var crashedStore = mock(OutboxStore.class);
    when(crashedStore.claim()).thenAnswer(invocation -> store.claim());
    when(crashedStore.published(any(), any()))
        .thenThrow(new IllegalStateException("SIMULATED_PROCESS_CRASH"));
    var crashingPublisher =
        new OutboxPublisher(
            crashedStore, transport, codec, routing, new LoanRequestFixMessageBuilder());
    assertThatThrownBy(crashingPublisher::publishOne).isInstanceOf(IllegalStateException.class);
    assertThat(status(event)).isEqualTo("PUBLISHING");
    expire(event);
    publisher.publishOne();
    assertThat(status(event)).isEqualTo("PUBLISHED");
    verify(transport, times(2)).send(eq("component-out"), eq(event.getPartitionKey()), anyString());
  }

  @Test
  void concurrentClaimsNeverShareOneLease() throws Exception {
    enqueue();
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(store::claim);
      var second = workers.submit(store::claim);
      assertThat(
              java.util.stream.Stream.of(first.get(), second.get())
                  .filter(Optional::isPresent)
                  .count())
          .isEqualTo(1);
    }
  }

  @Test
  void laterEventsForSameAggregateWaitForEarlierPublication() {
    var first = enqueue();
    var event = codec.decode(codec.json(first.getPayload()));
    event.setEventId(UUID.randomUUID().toString());
    transaction.executeWithoutResult(status -> outgoing.enqueue("STAGE2_COMPONENT", event));
    var claim = store.claim().orElseThrow();
    assertThat(claim.getOutboxId()).isEqualTo(first.getOutboxId());
    assertThat(store.claim()).isEmpty();
    store.published(claim, DeliveryReceipt.builder().partition(0).offset(1).build());
    assertThat(store.claim()).isPresent();
  }

  @Test
  void duplicatesCommitOneResponseAndAckEachDelivery() {
    var message = record(input());
    var ack = mock(Acknowledgment.class);
    receiver.receive(message, ack);
    receiver.receive(message, ack);
    assertThat(count("inbox_message")).isEqualTo(1);
    assertThat(count("outbox_event")).isEqualTo(1);
    assertThat(handler.invocations.get()).isEqualTo(1);
    verify(ack, times(2)).acknowledge();
    var response =
        jdbc.queryForMap(
            "SELECT correlation_id, payload FROM outbox_event WHERE service_name=?",
            properties.getServiceName());
    assertThat(response.get("correlation_id"))
        .isEqualTo(codec.decode(message.value()).getCorrelationId());
  }

  @Test
  void concurrentDuplicateConsumersCommitOneEffect() throws Exception {
    var message = record(input());
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> inbox.process(message));
      var second = workers.submit(() -> inbox.process(message));
      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
    }
    assertThat(count("outbox_event")).isEqualTo(1);
  }

  @Test
  void crashAfterCommitBeforeAckDoesNotRepeatEffects() {
    var message = record(input());
    var failedAck = mock(Acknowledgment.class);
    doThrow(new IllegalStateException("SIMULATED_PROCESS_CRASH")).when(failedAck).acknowledge();
    assertThatThrownBy(() -> receiver.receive(message, failedAck))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("outbox_event")).isEqualTo(1);
    receiver.receive(message, mock(Acknowledgment.class));
    assertThat(handler.invocations.get()).isEqualTo(1);
  }

  @Test
  void handlerFailureRollsBackInboxAuditAndResponseThenRecovers() {
    var message = record(input());
    handler.fail.set(true);
    assertThatThrownBy(() -> receiver.receive(message, mock(Acknowledgment.class)))
        .isInstanceOf(RetryableMessageException.class);
    assertThat(count("inbox_message")).isZero();
    assertThat(count("outbox_event")).isZero();
    assertThat(count("message_log")).isZero();
    handler.fail.set(false);
    assertThat(inbox.process(message)).isTrue();
    assertThat(count("outbox_event")).isEqualTo(1);
  }

  @Test
  void unfinishedReceivedRecordDoesNotSuppressRetry() {
    var event = input();
    var message = record(event);
    jdbc.update(
        "INSERT INTO inbox_message"
            + " (consumer_name,event_id,correlation_id,aggregate_reference,aggregate_version,event_type,payload,kafka_topic,kafka_partition,kafka_offset)"
            + " VALUES (?,?,?,?,?,?,CAST(? AS JSON),?,?,?)",
        properties.getConsumerName(),
        event.getEventId(),
        event.getCorrelationId(),
        event.getAggregateReference(),
        event.getAggregateVersion(),
        event.getEventType(),
        codec.json(codec.toMap(event)),
        message.topic(),
        0,
        message.offset());
    assertThat(inbox.process(message)).isTrue();
    assertThat(count("outbox_event")).isEqualTo(1);
  }

  @Test
  void distinctEventsWithSameVersionAreBothProcessed() {
    var first = input();
    var second = codec.decode(codec.encode(first));
    second.setEventId(UUID.randomUUID().toString());
    inbox.process(record(first));
    inbox.process(record(second));
    assertThat(count("inbox_message")).isEqualTo(2);
    assertThat(count("outbox_event")).isEqualTo(2);
  }

  @Test
  void reusedEventIdWithDifferentContentIsQuarantinedWithoutRepeatingEffects() {
    var event = input();
    inbox.process(record(event));
    event.setPayload(Map.of("tampered", true));
    receiver.receive(record(event), mock(Acknowledgment.class));
    assertThat(count("outbox_event")).isEqualTo(1);
    verify(transport)
        .send(
            eq("component-in.DLT"),
            anyString(),
            contains("EVENT_ID_REUSED_WITH_DIFFERENT_CONTENT"));
  }

  @Test
  void wrongDestinationAndUnsupportedTypeAreAuditedAndDeadLettered() {
    var event = input();
    event.setDestinationService("OTHER");
    receiver.receive(record(event), mock(Acknowledgment.class));
    event = input();
    event.setEventType("UNSUPPORTED");
    receiver.receive(record(event), mock(Acknowledgment.class));
    assertThat(count("inbox_message")).isZero();
    assertThat(count("message_log")).isEqualTo(2);
    verify(transport, times(2)).send(eq("component-in.DLT"), anyString(), anyString());
  }

  @Test
  void invalidEnvelopeIsRedactedAndPoisonDoesNotBlockHealthyMessage() {
    var poison =
        new ConsumerRecord<>(
            "component-in",
            0,
            offset.incrementAndGet(),
            "untrusted",
            "{\"access_token\":\"DO_NOT_STORE_THIS\"}");
    var ack = mock(Acknowledgment.class);
    receiver.receive(poison, ack);
    verify(ack).acknowledge();
    assertThat(
            jdbc.queryForObject(
                "SELECT payload FROM message_log WHERE service_name=?",
                String.class,
                properties.getServiceName()))
        .doesNotContain("DO_NOT_STORE_THIS");
    receiver.receive(record(input()), mock(Acknowledgment.class));
    assertThat(count("outbox_event")).isEqualTo(1);
    var invalidSchema = codec.toMap(input());
    invalidSchema.put("schemaVersion", 2);
    receiver.receive(
        new ConsumerRecord<>(
            "component-in", 0, offset.incrementAndGet(), "untrusted", codec.json(invalidSchema)),
        mock(Acknowledgment.class));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM message_log WHERE service_name=? AND"
                    + " processing_status='REJECTED'",
                Long.class,
                properties.getServiceName()))
        .isEqualTo(2);
  }

  @Test
  void failedDeadLetterDeliveryNeverAcknowledgesOriginal() {
    var poison =
        new ConsumerRecord<>("component-in", 0, offset.incrementAndGet(), "untrusted", "not json");
    var ack = mock(Acknowledgment.class);
    when(transport.send(anyString(), anyString(), anyString()))
        .thenThrow(new KafkaException("OFFLINE", new TimeoutException()));
    assertThatThrownBy(() -> receiver.receive(poison, ack)).isInstanceOf(KafkaException.class);
    verifyNoInteractions(ack);
    doReturn(DeliveryReceipt.builder().partition(0).offset(1).build())
        .when(transport)
        .send(anyString(), anyString(), anyString());
    receiver.receive(poison, ack);
    verify(ack).acknowledge();
    assertThat(count("message_log")).isEqualTo(1);
  }
}
