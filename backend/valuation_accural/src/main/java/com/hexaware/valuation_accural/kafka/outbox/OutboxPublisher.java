package com.hexaware.valuation_accural.kafka.outbox;

import com.hexaware.valuation_accural.kafka.event.EnvelopeCodec;
import com.hexaware.valuation_accural.kafka.event.EventRouting;
import com.hexaware.valuation_accural.kafka.producer.DeliveryReceipt;
import com.hexaware.valuation_accural.kafka.producer.EventTransport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {
  private final OutboxStore store;
  private final EventTransport transport;
  private final EnvelopeCodec codec;
  private final EventRouting routing;

  public boolean publishOne() {
    var candidate = store.claim();
    if (candidate.isEmpty()) return false;
    var event = candidate.get();
    DeliveryReceipt receipt;
    try {
      var envelope = codec.decode(codec.json(event.getPayload()));
      if (!routing.outgoingTopic(envelope).equals(event.getKafkaTopic())
          || !envelope.getEventId().equals(event.getEventId())
          || !envelope.getAggregateReference().equals(event.getPartitionKey())) {
        throw new IllegalStateException("OUTBOX_METADATA_MISMATCH");
      }
      receipt =
          transport.send(event.getKafkaTopic(), event.getPartitionKey(), codec.encode(envelope));
    } catch (RuntimeException failure) {
      store.failed(event, failure);
      return true;
    }
    // If the database fails or the process dies here, the lease recovers and delivery repeats.
    // Keep this outside the send catch: broker success is not a confirmed database commit.
    store.published(event, receipt);
    return true;
  }
}
