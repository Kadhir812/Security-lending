package com.hexaware.valuation_accural.kafka.event;

import com.hexaware.valuation_accural.exception.InvalidEnvelopeException;
import com.hexaware.valuation_accural.kafka.config.EventRoute;
import com.hexaware.valuation_accural.kafka.config.MessagingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventRouting {
  private final MessagingProperties properties;

  public String outgoingTopic(EventEnvelope event) {
    if (!properties.getServiceName().equals(event.getProducerService()))
      throw new InvalidEnvelopeException("WRONG_PRODUCER");
    var routes = properties.getRoutes().stream().filter(route -> matches(route, event)).toList();
    if (routes.size() != 1) throw new InvalidEnvelopeException("UNSUPPORTED_OUTBOUND_ROUTE");
    return routes.getFirst().getTopic();
  }

  public void validateIncoming(EventEnvelope event, String topic, String key) {
    if (!properties.getServiceName().equals(event.getDestinationService()))
      throw new InvalidEnvelopeException("WRONG_DESTINATION");
    if (!event.getAggregateReference().equals(key))
      throw new InvalidEnvelopeException("WRONG_PARTITION_KEY");
    if (properties.getRoutes().stream()
        .noneMatch(route -> matches(route, event) && route.getTopic().equals(topic))) {
      throw new InvalidEnvelopeException("UNSUPPORTED_INBOUND_ROUTE");
    }
  }

  private boolean matches(EventRoute route, EventEnvelope event) {
    return route.getProducerService().equals(event.getProducerService())
        && route.getDestinationService().equals(event.getDestinationService())
        && route.getEventType().equals(event.getEventType());
  }
}
