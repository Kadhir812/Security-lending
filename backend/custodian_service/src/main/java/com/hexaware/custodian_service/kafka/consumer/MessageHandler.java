package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.kafka.event.EventEnvelope;

/** Runs inside the inbox database transaction; no external side effects belong here. */
public interface MessageHandler {
  String eventType();

  void handle(EventEnvelope event);
}
