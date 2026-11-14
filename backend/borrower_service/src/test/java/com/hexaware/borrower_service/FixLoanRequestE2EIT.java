package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class FixLoanRequestE2EIT {
  private static final String REQUEST_TOPIC = "sec-lending-requests";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration FLOW_TIMEOUT = Duration.ofSeconds(60);

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();
  private final LoanRequestFixMessageBuilder fixMessages = new LoanRequestFixMessageBuilder();

  @Test
  void borrowerRequestPublishesFixU1AndCustodianPersistsIt() throws Exception {
    String borrowerUrl = environment("LOAN_E2E_BORROWER_URL", "http://localhost:8082");
    String custodianUrl = environment("LOAN_E2E_CUSTODIAN_URL", "http://localhost:8081");
    String borrowerToken = required("LOAN_E2E_BORROWER_TOKEN");
    String custodianToken = required("LOAN_E2E_CUSTODIAN_TOKEN");
    String mslaReference = required("LOAN_E2E_MSLA_REFERENCE");
    String securityIsin = required("LOAN_E2E_SECURITY_ISIN");
    String currency = environment("LOAN_E2E_CURRENCY", "INR");
    LocalDate settlementDate =
        LocalDate.parse(
            environment(
                "LOAN_FIX_SETTLEMENT_DATE",
                LocalDate.now(ZoneOffset.UTC).plusDays(3).toString()));
    String requestReference = "LR-FIX-" + UUID.randomUUID();
    String bootstrap =
        environment(
            "LOAN_FIX_KAFKA_BOOTSTRAP",
            environment("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));

    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "fix-u1-diagnostic-" + UUID.randomUUID());
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    properties.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

    try (KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(properties)) {
      consumer.subscribe(List.of(REQUEST_TOPIC));
      long assignmentDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      while (consumer.assignment().isEmpty() && System.nanoTime() < assignmentDeadline) {
        consumer.poll(Duration.ofMillis(200));
      }
      assertThat(consumer.assignment()).as("diagnostic consumer assigned").isNotEmpty();

      String body =
          json.writeValueAsString(
              Map.of(
                  "mslaReference", mslaReference,
                  "securityIsin", securityIsin,
                  "quantity", 100,
                  "requestedSettlementDate", settlementDate.toString(),
                  "settlementCurrency", currency));
      HttpResponse<String> submission =
          send(
              borrowerUrl,
              "/api/loan-requests",
              "POST",
              borrowerToken,
              requestReference,
              body);
      assertThat(submission.statusCode()).isEqualTo(201);
      JsonNode submitted = json.readTree(submission.body());
      assertThat(submitted.path("requestReference").asText()).isEqualTo(requestReference);

      ConsumerRecord<String, String> fixRecord =
          awaitFixRequest(consumer, requestReference, FLOW_TIMEOUT);
      JsonNode envelope = json.readTree(fixRecord.value());
      assertThat(envelope.path("eventType").asText()).isEqualTo("LOAN_REQUEST_SUBMITTED");
      assertThat(envelope.path("payload").size()).isEqualTo(1);
      String wireMessage = envelope.path("payload").path("fixMessage").asText();
      assertThat(wireMessage).contains("35=U1\u0001");
      var parsed = fixMessages.parseAndValidate(wireMessage);
      fixMessages.validateForEnvelope(
          com.hexaware.borrower_service.kafka.event.EventEnvelope.builder()
              .eventId(envelope.path("eventId").asText())
              .eventType(envelope.path("eventType").asText())
              .schemaVersion(envelope.path("schemaVersion").intValue())
              .producerService(envelope.path("producerService").asText())
              .destinationService(envelope.path("destinationService").asText())
              .correlationId(envelope.path("correlationId").asText())
              .aggregateReference(envelope.path("aggregateReference").asText())
              .aggregateVersion(envelope.path("aggregateVersion").longValue())
              .occurredAt(java.time.Instant.parse(envelope.path("occurredAt").asText()))
              .senderOrganizationReference(envelope.path("senderOrganizationReference").asText())
              .receiverOrganizationReference(envelope.path("receiverOrganizationReference").asText())
              .payload(Map.of("fixMessage", wireMessage))
              .build(),
          wireMessage);
      assertThat(parsed.getString(9001)).isEqualTo(requestReference);
      assertThat(parsed.getString(9002)).isEqualTo(mslaReference);
      assertThat(parsed.getString(48)).isEqualTo(securityIsin);
      assertThat(parsed.getString(38)).isEqualTo("100");
      assertThat(parsed.getString(15)).isEqualTo(currency);
      assertThat(parsed.getString(64)).isEqualTo(settlementDate.format(
          java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
      assertThat(parsed.getHeader().getString(49))
          .isEqualTo(envelope.path("senderOrganizationReference").asText());
      assertThat(parsed.getHeader().getString(56))
          .isEqualTo(envelope.path("receiverOrganizationReference").asText());
      System.out.printf(
          "FIX U1 observed: eventId=%s requestReference=%s topic=%s partition=%d offset=%d%n",
          envelope.path("eventId").asText(),
          requestReference,
          fixRecord.topic(),
          fixRecord.partition(),
          fixRecord.offset());

      JsonNode received =
          awaitCustodianReceipt(custodianUrl, custodianToken, requestReference, FLOW_TIMEOUT);
      assertThat(received.path("requestReference").asText()).isEqualTo(requestReference);
      assertThat(received.path("mslaReference").asText()).isEqualTo(mslaReference);
      assertThat(received.path("borrowerOrganizationReference").asText())
          .isEqualTo(envelope.path("senderOrganizationReference").asText());
      assertThat(received.path("custodianOrganizationReference").asText())
          .isEqualTo(envelope.path("receiverOrganizationReference").asText());
      assertThat(received.path("securityIsin").asText()).isEqualTo(securityIsin);
      assertThat(received.path("quantity").longValue()).isEqualTo(100);
      assertThat(received.path("requestedSettlementDate").asText())
          .isEqualTo(settlementDate.toString());
      assertThat(received.path("settlementCurrency").asText()).isEqualTo(currency);
      assertThat(received.path("status").asText()).isEqualTo("PENDING");
      System.out.printf(
          "Custodian receipt verified: requestReference=%s status=%s%n",
          requestReference, received.path("status").asText());
    }
  }

  private ConsumerRecord<String, String> awaitFixRequest(
      KafkaConsumer<String, String> consumer, String reference, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      for (var record : consumer.poll(Duration.ofMillis(500))) {
        JsonNode envelope = json.readTree(record.value());
        if ("LOAN_REQUEST_SUBMITTED".equals(envelope.path("eventType").asText())
            && reference.equals(envelope.path("correlationId").asText())) {
          return record;
        }
      }
    }
    throw new AssertionError("Timed out waiting for FIX U1 loan request on Kafka");
  }

  private JsonNode awaitCustodianReceipt(
      String baseUrl, String token, String reference, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      HttpResponse<String> response =
          send(
              baseUrl,
              "/api/loan-requests/" + reference,
              "GET",
              token,
              null,
              null);
      if (response.statusCode() == 200) return json.readTree(response.body());
      if (response.statusCode() != 404 && response.statusCode() != 503) {
        throw new AssertionError(
            "Custodian request query failed with HTTP " + response.statusCode());
      }
      Thread.sleep(250);
    }
    throw new AssertionError("Timed out waiting for Custodian to persist the FIX loan request");
  }

  private HttpResponse<String> send(
      String baseUrl,
      String path,
      String method,
      String token,
      String idempotencyKey,
      String body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json");
    if (idempotencyKey != null) {
      builder.header("Idempotency-Key", idempotencyKey);
    }
    if ("POST".equals(method)) {
      builder.header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body));
    } else {
      builder.GET();
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Set " + name + " for the FIX U1 integration test");
    }
    return value;
  }

  private static String environment(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
