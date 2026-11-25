package com.hexaware.valuation_accural.kafka.producer;

public interface EventTransport {
  DeliveryReceipt send(String topic, String key, String json);
}
