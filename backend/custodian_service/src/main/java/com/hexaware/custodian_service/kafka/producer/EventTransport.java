package com.hexaware.custodian_service.kafka.producer;

public interface EventTransport {
  DeliveryReceipt send(String topic, String key, String json);
}
