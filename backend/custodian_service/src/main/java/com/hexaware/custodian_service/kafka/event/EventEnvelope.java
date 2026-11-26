package com.hexaware.custodian_service.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventEnvelope {
  @NotNull
  @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
  private String eventId;

  @NotBlank
  @Size(max = 100)
  private String eventType;

  @NotNull
  @Min(1)
  @Max(1)
  private Integer schemaVersion;

  @NotNull
  @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}")
  private String producerService;

  @NotNull
  @Pattern(regexp = "[A-Z][A-Z0-9_]{0,79}")
  private String destinationService;

  @NotBlank
  @Size(max = 80)
  private String correlationId;

  @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
  private String causationEventId;

  @NotBlank
  @Size(max = 80)
  private String aggregateReference;

  @NotNull @PositiveOrZero private Long aggregateVersion;
  @NotNull private Instant occurredAt;

  @Size(max = 80)
  private String senderOrganizationReference;

  @Size(max = 80)
  private String receiverOrganizationReference;

  @NotNull @ToString.Exclude private Map<String, Object> payload;
}
