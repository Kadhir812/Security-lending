package com.hexaware.valuation_accural.kafka.consumer;

import com.hexaware.valuation_accural.kafka.event.EventEnvelope;

/** Runs inside the inbox database transaction; no external side effects belong here. */
public interface MessageHandler {
  String eventType();

  void handle(EventEnvelope event);
}
