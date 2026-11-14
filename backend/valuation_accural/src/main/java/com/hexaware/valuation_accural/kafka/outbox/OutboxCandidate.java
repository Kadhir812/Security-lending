package com.hexaware.valuation_accural.kafka.outbox;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OutboxCandidate {
  private long id;
  private int attemptCount;
}
