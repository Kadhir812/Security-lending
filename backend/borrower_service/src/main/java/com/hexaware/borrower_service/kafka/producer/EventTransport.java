package com.hexaware.borrower_service.kafka.producer;

public interface EventTransport {
  DeliveryReceipt send(String topic, String key, String json);
}
