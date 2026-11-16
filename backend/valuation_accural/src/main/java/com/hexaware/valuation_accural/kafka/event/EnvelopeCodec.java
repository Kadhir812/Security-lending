package com.hexaware.valuation_accural.kafka.event;

import com.hexaware.valuation_accural.exception.InvalidEnvelopeException;
import com.hexaware.valuation_accural.kafka.config.MessagingProperties;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class EnvelopeCodec {
  private static final Set<String> SECRET_KEYS =
      Set.of(
          "authorization",
          "password",
          "token",
          "accesstoken",
          "refreshtoken",
          "idtoken",
          "clientsecret",
          "secret",
          "credentials");
  private final ObjectMapper mapper;
  private final Validator validator;
  private final MessagingProperties properties;

  public EventEnvelope decode(String json) {
    if (json == null
        || json.getBytes(StandardCharsets.UTF_8).length > properties.getMaxEnvelopeBytes()) {
      throw new InvalidEnvelopeException("ENVELOPE_SIZE");
    }
    try {
      var tree = mapper.readTree(json);
      if (!tree.isObject()
          || !tree.path("schemaVersion").isIntegralNumber()
          || !tree.path("aggregateVersion").isIntegralNumber()
          || !tree.path("payload").isObject()) {
        throw new InvalidEnvelopeException("ENVELOPE_TYPES");
      }
      for (String name :
          List.of(
              "eventId",
              "eventType",
              "producerService",
              "destinationService",
              "correlationId",
              "aggregateReference",
              "occurredAt")) {
        if (!tree.path(name).isTextual()) throw new InvalidEnvelopeException("ENVELOPE_TYPES");
      }
      EventEnvelope event = mapper.treeToValue(tree, EventEnvelope.class);
      validate(event);
      return event;
    } catch (InvalidEnvelopeException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new InvalidEnvelopeException("MALFORMED_JSON");
    }
  }

  public void validate(EventEnvelope event) {
    if (event == null || !validator.validate(event).isEmpty())
      throw new InvalidEnvelopeException("ENVELOPE_VALIDATION");
    rejectSecrets(event.getPayload());
    if (mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8).length
        > properties.getMaxEnvelopeBytes()) {
      throw new InvalidEnvelopeException("ENVELOPE_SIZE");
    }
  }

  private void rejectSecrets(Object value) {
    if (value instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        String key =
            String.valueOf(entry.getKey()).replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        if (SECRET_KEYS.contains(key)) throw new InvalidEnvelopeException("SENSITIVE_PAYLOAD");
        rejectSecrets(entry.getValue());
      }
    } else if (value instanceof Iterable<?> values) {
      values.forEach(this::rejectSecrets);
    }
  }

  public Map<String, Object> toMap(EventEnvelope event) {
    validate(event);
    return mapper.readValue(
        mapper.writeValueAsString(event), new TypeReference<Map<String, Object>>() {});
  }

  public String encode(EventEnvelope event) {
    validate(event);
    return mapper.writeValueAsString(event);
  }

  public String json(Object value) {
    return mapper.writeValueAsString(value);
  }
}
