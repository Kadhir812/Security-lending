package com.hexaware.custodian_service;

import com.hexaware.custodian_service.kafka.consumer.MessageHandler;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;

/** Launched only by the integration-test harness; absent from the production JAR. */
public class CustodianMessagingFixture {
  @TestConfiguration(proxyBeanMethods = false)
  static class Handlers {
    @Bean
    MessageHandler ping(OutgoingEventService outgoing) {
      return new MessageHandler() {
        public String eventType() {
          return "STAGE2_PING";
        }

        public void handle(EventEnvelope input) {
          outgoing.enqueue(
              "STAGE2_ROUNDTRIP",
              EventEnvelope.builder()
                  .eventId(
                      UUID.nameUUIDFromBytes(
                              (input.getEventId() + ":response").getBytes(StandardCharsets.UTF_8))
                          .toString())
                  .eventType("STAGE2_PONG")
                  .schemaVersion(1)
                  .producerService("CUSTODIAN")
                  .destinationService("BORROWER")
                  .correlationId(input.getCorrelationId())
                  .causationEventId(input.getEventId())
                  .aggregateReference(input.getAggregateReference())
                  .aggregateVersion(input.getAggregateVersion())
                  .occurredAt(input.getOccurredAt())
                  .senderOrganizationReference(input.getReceiverOrganizationReference())
                  .receiverOrganizationReference(input.getSenderOrganizationReference())
                  .payload(Map.of("accepted", true))
                  .build());
        }
      };
    }
  }

  public static void main(String[] arguments) throws Exception {
    Path directory = Path.of(arguments[0]);
    String runId = arguments[1];
    var settings = new HashMap<String, Object>();
    PersistenceIT.database((name, value) -> settings.put(name, value.get()));
    settings.put("server.port", 0);
    settings.put("app.messaging.consumer-enabled", true);
    settings.put("app.messaging.publisher-enabled", true);
    settings.put("app.messaging.bootstrap-servers", System.getenv("STAGE2_KAFKA_BOOTSTRAP"));
    String requestsTopic = "sec-lending-requests-" + runId;
    String responsesTopic = "sec-lending-responses-" + runId;
    settings.put("app.messaging.inbound-topic", requestsTopic);
    settings.put("app.messaging.dead-letter-topic", requestsTopic + ".DLT");
    settings.put("app.messaging.group-id", "custodian-stage2-" + runId);
    settings.put("app.messaging.consumer-name", "custodian-stage2-" + runId);
    settings.put("app.messaging.poll-interval-ms", 100);
    settings.put("app.messaging.initial-backoff-ms", 100);
    settings.put("app.messaging.max-backoff-ms", 1000);
    settings.put("app.messaging.routes[0].event-type", "STAGE2_PING");
    settings.put("app.messaging.routes[0].producer-service", "BORROWER");
    settings.put("app.messaging.routes[0].destination-service", "CUSTODIAN");
    settings.put("app.messaging.routes[0].topic", requestsTopic);
    settings.put("app.messaging.routes[1].event-type", "STAGE2_PONG");
    settings.put("app.messaging.routes[1].producer-service", "CUSTODIAN");
    settings.put("app.messaging.routes[1].destination-service", "BORROWER");
    settings.put("app.messaging.routes[1].topic", responsesTopic);
    var application = new SpringApplication(CustodianServiceApplication.class, Handlers.class);
    application.addInitializers(
        context ->
            context
                .getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("fixture", settings)));
    try (var context = application.run()) {
      Files.writeString(directory.resolve("custodian.ready"), "ready");
      while (!Files.exists(directory.resolve("stop"))) Thread.sleep(100);
    }
  }
}
