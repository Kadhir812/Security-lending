package com.hexaware.valuation_accural.kafka.producer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryReceipt {
  private int partition;
  private long offset;
}
