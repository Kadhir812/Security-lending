package com.hexaware.valuation_accural.kafka.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("app.messaging")
public class MessagingProperties {
  @NotBlank
  @Size(max = 80)
  private String serviceName;

  @NotBlank
  @Size(max = 150)
  private String consumerName;

  @NotBlank private String groupId;
  private boolean consumerEnabled;
  private boolean publisherEnabled;
  private String inboundTopic = "";
  private String deadLetterTopic = "";
  @NotEmpty private List<String> bootstrapServers = List.of("localhost:9092");
  @ToString.Exclude private Map<String, Object> clientProperties = new HashMap<>();
  @Valid private List<EventRoute> routes = new ArrayList<>();

  @Min(1)
  @Max(100)
  private int batchSize = 20;

  @Min(1)
  @Max(100)
  private int maxAttempts = 8;

  @Min(100)
  @Max(60000)
  private long sendTimeoutMs = 5000;

  @Min(1000)
  private long leaseMs = 30000;

  @Min(10)
  private long initialBackoffMs = 1000;

  @Min(10)
  private long maxBackoffMs = 60000;

  @Min(0)
  @Max(20)
  private int consumerRetries = 3;

  @Min(1024)
  @Max(1048576)
  private int maxEnvelopeBytes = 262144;

  @AssertTrue(message = "Lease must exceed three send timeouts; backoff bounds must be ordered")
  public boolean isTimingValid() {
    return leaseMs > 3 * sendTimeoutMs && maxBackoffMs >= initialBackoffMs;
  }

  @AssertTrue(
      message =
          "Enabled consumers require distinct inbound/dead-letter topics and an inbound allowlist")
  public boolean isConsumerRoutingValid() {
    return !consumerEnabled
        || (!inboundTopic.isBlank()
            && !deadLetterTopic.isBlank()
            && !inboundTopic.equals(deadLetterTopic)
            && routes.stream()
                .anyMatch(
                    route ->
                        serviceName.equals(route.getDestinationService())
                            && inboundTopic.equals(route.getTopic())));
  }
}
