package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.exception.RetryableMessageException;
import com.hexaware.borrower_service.kafka.config.KafkaMessagingConfiguration;
import com.hexaware.borrower_service.kafka.config.MessagingProperties;
import com.hexaware.borrower_service.kafka.consumer.DeadLetterService;
import com.hexaware.borrower_service.kafka.consumer.MessageHandler;
import com.hexaware.borrower_service.kafka.consumer.MessageReceiver;
import com.hexaware.borrower_service.kafka.event.EnvelopeCodec;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.kafka.event.EventRouting;
import com.hexaware.borrower_service.kafka.inbox.InboxProcessor;
import com.hexaware.borrower_service.kafka.outbox.OutboxPublisher;
import com.hexaware.borrower_service.kafka.outbox.OutboxStore;
import com.hexaware.borrower_service.kafka.producer.EventTransport;
import com.hexaware.borrower_service.kafka.producer.KafkaEventTransport;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import java.io.File;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = {BorrowerServiceApplication.class, RoundTripIT.Fixtures.class})
class RoundTripIT {
  static final String RUN_ID = UUID.randomUUID().toString();
  static final String BORROWER_GROUP = "borrower-stage2-" + RUN_ID;
  static final String CUSTODIAN_GROUP = "custodian-stage2-" + RUN_ID;
  static final String REQUESTS = "sec-lending-requests-" + RUN_ID;
  static final String RESPONSES = "sec-lending-responses-" + RUN_ID;
  static final AtomicBoolean FAIL_ACK_ONCE = new AtomicBoolean(true);
  static final AtomicInteger HANDLER_CALLS = new AtomicInteger();
  static final AtomicInteger RETRY_CALLS = new AtomicInteger();

  @DynamicPropertySource
  static void settings(DynamicPropertyRegistry registry) throws Exception {
    PersistenceIT.database(registry);
    String bootstrap = System.getenv("STAGE2_KAFKA_BOOTSTRAP");
    if (bootstrap == null || bootstrap.isBlank())
      throw new IllegalStateException("STAGE2_KAFKA_BOOTSTRAP must identify a disposable broker");
    try (var admin = Admin.create(Map.of("bootstrap.servers", bootstrap))) {
      var existing = admin.listTopics().names().get(15, TimeUnit.SECONDS);
      var topics =
          List.of(REQUESTS, RESPONSES, REQUESTS + ".DLT", RESPONSES + ".DLT").stream()
              .filter(topic -> !existing.contains(topic))
              .map(topic -> new NewTopic(topic, 1, (short) 1))
              .toList();
      if (!topics.isEmpty()) admin.createTopics(topics).all().get(15, TimeUnit.SECONDS);
    }
    registry.add("app.messaging.bootstrap-servers", () -> bootstrap);
    registry.add("app.messaging.consumer-enabled", () -> true);
    registry.add("app.messaging.publisher-enabled", () -> false);
    registry.add("app.messaging.group-id", () -> BORROWER_GROUP);
    registry.add("app.messaging.consumer-name", () -> BORROWER_GROUP);
    registry.add("app.messaging.inbound-topic", () -> RESPONSES);
    registry.add("app.messaging.dead-letter-topic", () -> RESPONSES + ".DLT");
    registry.add("app.messaging.initial-backoff-ms", () -> 100);
    registry.add("app.messaging.max-backoff-ms", () -> 1000);
    registry.add("app.messaging.consumer-retries", () -> 2);
    registry.add("app.messaging.routes[0].event-type", () -> "STAGE2_PING");
    registry.add("app.messaging.routes[0].producer-service", () -> "BORROWER");
    registry.add("app.messaging.routes[0].destination-service", () -> "CUSTODIAN");
    registry.add("app.messaging.routes[0].topic", () -> REQUESTS);
    registry.add("app.messaging.routes[1].event-type", () -> "STAGE2_PONG");
    registry.add("app.messaging.routes[1].producer-service", () -> "CUSTODIAN");
    registry.add("app.messaging.routes[1].destination-service", () -> "BORROWER");
    registry.add("app.messaging.routes[1].topic", () -> RESPONSES);
    registry.add("app.messaging.routes[2].event-type", () -> "LOAN_REQUEST_DECISION");
    registry.add("app.messaging.routes[2].producer-service", () -> "CUSTODIAN");
    registry.add("app.messaging.routes[2].destination-service", () -> "BORROWER");
    registry.add("app.messaging.routes[2].topic", () -> RESPONSES);
    registry.add("app.messaging.routes[3].event-type", () -> "STAGE2_RETRY");
    registry.add("app.messaging.routes[3].producer-service", () -> "CUSTODIAN");
    registry.add("app.messaging.routes[3].destination-service", () -> "BORROWER");
    registry.add("app.messaging.routes[3].topic", () -> RESPONSES);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fixtures {
    @Bean
    MessageHandler pong() {
      return new MessageHandler() {
        public String eventType() {
          return "STAGE2_PONG";
        }

        public void handle(EventEnvelope event) {
          HANDLER_CALLS.incrementAndGet();
        }
      };
    }

    @Bean
    MessageHandler retryProbe() {
      return new MessageHandler() {
        public String eventType() {
          return "STAGE2_RETRY";
        }

        public void handle(EventEnvelope event) {
          RETRY_CALLS.incrementAndGet();
          throw new RetryableMessageException();
        }
      };
    }

    @Bean
    @Primary
    MessageReceiver crashAfterCommit(InboxProcessor inbox, DeadLetterService deadLetters) {
      return new MessageReceiver(inbox, deadLetters) {
        @Override
        public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
          super.receive(
              record,
              () -> {
                if (FAIL_ACK_ONCE.getAndSet(false)) throw new RetryableMessageException();
                acknowledgment.acknowledge();
              });
        }
      };
    }
  }

  @Autowired OutgoingEventService outgoing;
  @Autowired OutboxStore store;
  @Autowired OutboxPublisher publisher;
  @Autowired EventTransport transport;
  @Autowired EnvelopeCodec codec;
  @Autowired EventRouting routing;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;

  EventEnvelope request() {
    return EventEnvelope.builder()
        .eventId(UUID.randomUUID().toString())
        .eventType("STAGE2_PING")
        .schemaVersion(1)
        .producerService("BORROWER")
        .destinationService("CUSTODIAN")
        .correlationId(UUID.randomUUID().toString())
        .aggregateReference("stage2-" + UUID.randomUUID())
        .aggregateVersion(1L)
        .occurredAt(Instant.now())
        .senderOrganizationReference("BORROWER-TEST")
        .receiverOrganizationReference("CUSTODIAN-TEST")
        .payload(Map.of("probe", true))
        .build();
  }

  void await(String description, BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) return;
      Thread.sleep(100);
    }
    throw new AssertionError("Timed out: " + description);
  }

  String responseId(EventEnvelope request) {
    return UUID.nameUUIDFromBytes(
            (request.getEventId() + ":response").getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  long received(String id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM inbox_message WHERE consumer_name=? AND event_id=? AND"
            + " status='PROCESSED'",
        Long.class,
        BORROWER_GROUP,
        id);
  }

  String outboxStatus(long id) {
    return jdbc.queryForObject(
        "SELECT status FROM outbox_event WHERE outbox_id=?", String.class, id);
  }

  boolean committed(String group, String topic, long offset) {
    try (var admin =
        Admin.create(Map.of("bootstrap.servers", System.getenv("STAGE2_KAFKA_BOOTSTRAP")))) {
      var offsets =
          admin
              .listConsumerGroupOffsets(group)
              .partitionsToOffsetAndMetadata()
              .get(5, TimeUnit.SECONDS);
      var position = offsets.get(new org.apache.kafka.common.TopicPartition(topic, 0));
      return position != null && position.offset() > offset;
    } catch (Exception exception) {
      return false;
    }
  }

  // Administrative fixture assertions only. Neither application has these cross-database
  // credentials.
  String custodianValue(String sql, String parameter) {
    String server = System.getenv("STAGE1_MYSQL_URL");
    try (var connection =
            DriverManager.getConnection(
                server + "custodian_db",
                System.getenv().getOrDefault("STAGE1_MYSQL_ADMIN", "root"),
                System.getenv().getOrDefault("STAGE1_MYSQL_ADMIN_PASSWORD", ""));
        var statement = connection.prepareStatement(sql)) {
      statement.setString(1, parameter);
      try (var rows = statement.executeQuery()) {
        return rows.next() ? rows.getString(1) : null;
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Fixture database assertion failed", exception);
    }
  }

  Process startCustodian(Path directory) throws Exception {
    Path module = Path.of("../custodian_service").toAbsolutePath().normalize();
    String dependencies = Files.readString(module.resolve("target/test-classpath.txt")).trim();
    String classpath =
        module.resolve("target/test-classes")
            + File.pathSeparator
            + module.resolve("target/classes")
            + File.pathSeparator
            + dependencies;
    String java =
        Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
            .toString();
    return new ProcessBuilder(
            java,
            "-cp",
            classpath,
            "com.hexaware.custodian_service.CustodianMessagingFixture",
            directory.toString(),
            RUN_ID)
        .directory(module.toFile())
        .redirectErrorStream(true)
        .redirectOutput(directory.resolve("custodian.log").toFile())
        .start();
  }

  @Test
  void realBrokerRoundTripSurvivesOutagesCrashWindowsDuplicatesAndPoison() throws Exception {
    Path directory = Path.of("../../.stage2/roundtrip-" + RUN_ID).toAbsolutePath().normalize();
    Files.createDirectories(directory);
    var request = request();
    var row =
        new TransactionTemplate(manager)
            .execute(status -> outgoing.enqueue("STAGE2_ROUNDTRIP", request));

    // Exercise the real Kafka producer against an unavailable endpoint, then recover to the live
    // broker.
    int unusedPort;
    try (var socket = new ServerSocket(0)) {
      unusedPort = socket.getLocalPort();
    }
    var unavailable = new MessagingProperties();
    unavailable.setBootstrapServers(List.of("127.0.0.1:" + unusedPort));
    unavailable.setSendTimeoutMs(500);
    var factory = new KafkaMessagingConfiguration().messagingProducerFactory(unavailable);
    var template = new KafkaTemplate<String, String>(factory);
    try {
      new OutboxPublisher(store, new KafkaEventTransport(template, unavailable), codec, routing)
          .publishOne();
    } finally {
      template.destroy();
      ((DefaultKafkaProducerFactory<String, String>) factory).destroy();
    }
    assertThat(outboxStatus(row.getOutboxId())).isEqualTo("PENDING");
    jdbc.update(
        "UPDATE outbox_event SET next_attempt_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE"
            + " outbox_id=?",
        row.getOutboxId());

    var crashedStore = mock(OutboxStore.class);
    when(crashedStore.claim()).thenAnswer(invocation -> store.claim());
    when(crashedStore.published(any(), any()))
        .thenThrow(new IllegalStateException("SIMULATED_CRASH_AFTER_BROKER_ACK"));
    assertThatThrownBy(
            () -> new OutboxPublisher(crashedStore, transport, codec, routing).publishOne())
        .isInstanceOf(IllegalStateException.class);
    assertThat(outboxStatus(row.getOutboxId())).isEqualTo("PUBLISHING");
    jdbc.update(
        "UPDATE outbox_event SET locked_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE"
            + " outbox_id=?",
        row.getOutboxId());
    publisher.publishOne();
    assertThat(outboxStatus(row.getOutboxId())).isEqualTo("PUBLISHED");
    assertThat(received(responseId(request)))
        .isZero(); // Custodian has been down for both deliveries.

    Process custodian = startCustodian(directory);
    try {
      await(
          "Custodian startup; inspect " + directory.resolve("custodian.log"),
          () -> Files.exists(directory.resolve("custodian.ready")) || !custodian.isAlive());
      assertThat(custodian.isAlive()).as("Custodian fixture startup").isTrue();
      await("response inbox commit", () -> received(responseId(request)) == 1);
      await(
          "Custodian response publish",
          () ->
              "PUBLISHED"
                  .equals(
                      custodianValue(
                          "SELECT status FROM outbox_event WHERE event_id=?",
                          responseId(request))));
      assertThat(FAIL_ACK_ONCE.get()).isFalse();
      assertThat(HANDLER_CALLS.get()).isEqualTo(1);
      assertThat(
              custodianValue(
                  "SELECT correlation_id FROM inbox_message WHERE event_id=?",
                  request.getEventId()))
          .isEqualTo(request.getCorrelationId());
      String responseJson =
          custodianValue("SELECT payload FROM outbox_event WHERE event_id=?", responseId(request));
      var response = codec.decode(responseJson);
      assertThat(response.getCorrelationId()).isEqualTo(request.getCorrelationId());
      assertThat(response.getCausationEventId()).isEqualTo(request.getEventId());

      var duplicateRequest =
          transport.send(REQUESTS, request.getAggregateReference(), codec.encode(request));
      await(
          "duplicate request acknowledgment",
          () -> committed(CUSTODIAN_GROUP, REQUESTS, duplicateRequest.getOffset()));
      assertThat(
              custodianValue(
                  "SELECT COUNT(*) FROM outbox_event WHERE event_id=?", responseId(request)))
          .isEqualTo("1");
      var duplicateResponse =
          transport.send(RESPONSES, response.getAggregateReference(), responseJson);
      await(
          "duplicate response acknowledgment",
          () -> committed(BORROWER_GROUP, RESPONSES, duplicateResponse.getOffset()));
      assertThat(received(response.getEventId())).isEqualTo(1);
      assertThat(HANDLER_CALLS.get()).isEqualTo(1);

      var poison = transport.send(REQUESTS, "invalid", "{\"access_token\":\"NEVER_PERSIST_THIS\"}");
      var healthy = request();
      var healthyRow =
          new TransactionTemplate(manager)
              .execute(status -> outgoing.enqueue("STAGE2_ROUNDTRIP", healthy));
      await(
          "healthy publish",
          () -> {
            publisher.publishOne();
            return "PUBLISHED".equals(outboxStatus(healthyRow.getOutboxId()));
          });
      await("healthy message after poison", () -> received(responseId(healthy)) == 1);
      String rejectionId =
          UUID.nameUUIDFromBytes(
                  (CUSTODIAN_GROUP + ":" + REQUESTS + ":0:" + poison.getOffset())
                      .getBytes(StandardCharsets.UTF_8))
              .toString();
      String rejection =
          custodianValue("SELECT payload FROM message_log WHERE event_id=?", rejectionId);
      assertThat(rejection).contains("originalSha256").doesNotContain("NEVER_PERSIST_THIS");
      try (var consumer =
          new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
              Map.of(
                  "bootstrap.servers",
                  System.getenv("STAGE2_KAFKA_BOOTSTRAP"),
                  "group.id",
                  "dlt-proof-" + RUN_ID,
                  "key.deserializer",
                  org.apache.kafka.common.serialization.StringDeserializer.class,
                  "value.deserializer",
                  org.apache.kafka.common.serialization.StringDeserializer.class,
                  "enable.auto.commit",
                  false,
                  "auto.offset.reset",
                  "earliest"))) {
        consumer.subscribe(List.of(REQUESTS + ".DLT"));
        var found = new AtomicBoolean();
        await(
            "dead-letter broker record",
            () -> {
              consumer
                  .poll(Duration.ofMillis(250))
                  .forEach(
                      record -> {
                        if (rejectionId.equals(record.key())) {
                          assertThat(record.value()).doesNotContain("NEVER_PERSIST_THIS");
                          found.set(true);
                        }
                      });
              return found.get();
            });
      }
      verifyActualLoanConsumerQuarantine();
      Files.writeString(
          directory.resolve("evidence.json"),
          codec.json(
              Map.of(
                  "requestId",
                  request.getEventId(),
                  "responseId",
                  response.getEventId(),
                  "correlationId",
                  request.getCorrelationId(),
                  "borrowerConsumer",
                  BORROWER_GROUP,
                  "custodianConsumer",
                  CUSTODIAN_GROUP,
                  "rejectionId",
                  rejectionId,
                  "passed",
                  true)));
    } finally {
      Files.writeString(directory.resolve("stop"), "stop");
      if (!custodian.waitFor(15, TimeUnit.SECONDS)) {
        custodian.destroy();
        if (!custodian.waitFor(5, TimeUnit.SECONDS)) custodian.destroyForcibly();
      }
    }
  }

  private void verifyActualLoanConsumerQuarantine() throws Exception {
      var malformedLoan =
          transport.send(RESPONSES, "malformed-loan", "{\"eventType\":\"LOAN_REQUEST_DECISION\"");
      var unsupportedLoan = request();
      unsupportedLoan.setEventType("LOAN_REQUEST_UNSUPPORTED");
      unsupportedLoan.setProducerService("CUSTODIAN");
      unsupportedLoan.setDestinationService("BORROWER");
      unsupportedLoan.setPayload(Map.of("requestReference", "unsupported-" + RUN_ID));
      var unsupported =
          transport.send(
              RESPONSES,
              unsupportedLoan.getAggregateReference(),
              codec.encode(unsupportedLoan));

      var malformedDecision = request();
      malformedDecision.setEventType("LOAN_REQUEST_DECISION");
      malformedDecision.setProducerService("CUSTODIAN");
      malformedDecision.setDestinationService("BORROWER");
      malformedDecision.setAggregateReference("unknown-loan-" + RUN_ID);
      malformedDecision.setCorrelationId(malformedDecision.getAggregateReference());
      malformedDecision.setSenderOrganizationReference("CUSTODIAN-TEST");
      malformedDecision.setReceiverOrganizationReference("BORROWER-TEST");
      malformedDecision.setPayload(
          Map.of(
              "payloadVersion", 1,
              "requestReference", malformedDecision.getAggregateReference(),
              "borrowerOrganizationReference", "BORROWER-TEST",
              "custodianOrganizationReference", "CUSTODIAN-TEST"));
      var invalidDecision =
          transport.send(
              RESPONSES,
              malformedDecision.getAggregateReference(),
              codec.encode(malformedDecision));

      var retryEvent = request();
      retryEvent.setEventType("STAGE2_RETRY");
      retryEvent.setProducerService("CUSTODIAN");
      retryEvent.setDestinationService("BORROWER");
      var retry = transport.send(RESPONSES, retryEvent.getAggregateReference(), codec.encode(retryEvent));
      var valid = request();
      valid.setEventType("STAGE2_PONG");
      valid.setProducerService("CUSTODIAN");
      valid.setDestinationService("BORROWER");
      int expectedHandlerCalls = HANDLER_CALLS.get() + 1;
      var validReceipt = transport.send(RESPONSES, valid.getAggregateReference(), codec.encode(valid));

      String malformedId = rejectionId(RESPONSES, malformedLoan.getOffset());
      String unsupportedId = rejectionId(RESPONSES, unsupported.getOffset());
      String invalidDecisionId = rejectionId(RESPONSES, invalidDecision.getOffset());
      String retryId = rejectionId(RESPONSES, retry.getOffset());
      await(
          "loan-event rejection audits",
          () ->
              rejected(malformedId)
                  && rejected(unsupportedId)
                  && rejected(invalidDecisionId)
                  && rejected(retryId));
      await("valid event after loan poison", () -> received(valid.getEventId()) == 1);
      assertThat(RETRY_CALLS.get()).isEqualTo(3);
      assertThat(HANDLER_CALLS.get()).isEqualTo(expectedHandlerCalls);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM inbox_message WHERE consumer_name=? AND event_id IN (?,?,?,?)",
                  Long.class,
                  BORROWER_GROUP,
                  malformedId,
                  unsupportedLoan.getEventId(),
                  malformedDecision.getEventId(),
                  retryEvent.getEventId()))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM outbox_event WHERE service_name='BORROWER' AND"
                      + " aggregate_reference IN (?,?,?,?)",
                  Long.class,
                  "malformed-loan",
                  unsupportedLoan.getAggregateReference(),
                  malformedDecision.getAggregateReference(),
                  retryEvent.getAggregateReference()))
          .isZero();
      assertThat(committed(BORROWER_GROUP, RESPONSES, validReceipt.getOffset())).isTrue();

      try (var consumer =
          new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
              Map.of(
                  "bootstrap.servers",
                  System.getenv("STAGE2_KAFKA_BOOTSTRAP"),
                  "group.id",
                  "loan-dlt-proof-" + RUN_ID,
                  "key.deserializer",
                  org.apache.kafka.common.serialization.StringDeserializer.class,
                  "value.deserializer",
                  org.apache.kafka.common.serialization.StringDeserializer.class,
                  "enable.auto.commit",
                  false,
                  "auto.offset.reset",
                  "earliest"))) {
        consumer.subscribe(List.of(RESPONSES + ".DLT"));
        var found = new java.util.HashSet<String>();
        Set<String> expected =
            Set.of(malformedId, unsupportedId, invalidDecisionId, retryId);
        await(
            "all loan consumer records confirmed on DLT",
            () -> {
              consumer
                  .poll(Duration.ofMillis(250))
                  .forEach(
                      record -> {
                        if (expected.contains(record.key())) {
                          assertThat(record.value())
                              .doesNotContain("LOAN_REQUEST_UNSUPPORTED")
                              .doesNotContain("unknown-loan-");
                          found.add(record.key());
                        }
                      });
              return found.containsAll(expected);
            });
      }
    }

  private String rejectionId(String topic, long offset) {
      return UUID.nameUUIDFromBytes(
              (BORROWER_GROUP + ":" + topic + ":0:" + offset)
                  .getBytes(StandardCharsets.UTF_8))
          .toString();
    }

  private boolean rejected(String rejectionId) {
      return jdbc.queryForObject(
              "SELECT COUNT(*) FROM message_log WHERE event_id=? AND processing_status='REJECTED'",
              Long.class,
              rejectionId)
          == 1;
  }
}
