package com.hexaware.borrower_service.kafka.producer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryReceipt {
  private int partition;
  private long offset;
}
