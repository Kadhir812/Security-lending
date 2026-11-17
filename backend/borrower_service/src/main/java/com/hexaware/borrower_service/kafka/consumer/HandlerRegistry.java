package com.hexaware.borrower_service.kafka.consumer;

import com.hexaware.borrower_service.exception.InvalidEnvelopeException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HandlerRegistry {
  private final Map<String, MessageHandler> handlers = new HashMap<>();

  public HandlerRegistry(List<MessageHandler> handlers) {
    for (MessageHandler handler : handlers) {
      if (this.handlers.putIfAbsent(handler.eventType(), handler) != null)
        throw new IllegalStateException("Duplicate event handler");
    }
  }

  public MessageHandler required(String eventType) {
    MessageHandler handler = handlers.get(eventType);
    if (handler == null) throw new InvalidEnvelopeException("UNSUPPORTED_EVENT_TYPE");
    return handler;
  }
}
