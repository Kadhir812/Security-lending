package com.hexaware.borrower_service.kafka.outbox;

import com.hexaware.borrower_service.kafka.event.EnvelopeCodec;
import com.hexaware.borrower_service.kafka.event.EventRouting;
import com.hexaware.borrower_service.kafka.producer.DeliveryReceipt;
import com.hexaware.borrower_service.kafka.producer.EventTransport;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxStore store;
  private final EventTransport transport;
  private final EnvelopeCodec codec;
  private final EventRouting routing;
  private final LoanRequestFixMessageBuilder fixMessages;

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
      if ("LOAN_REQUEST_SUBMITTED".equals(envelope.getEventType())) {
        Object existingMessage = envelope.getPayload().get("fixMessage");
        String wireMessage;
        if (existingMessage instanceof String fixMessage) {
          wireMessage = fixMessage;
        } else {
          wireMessage = fixMessages.fromLegacyEnvelope(envelope);
        }
        fixMessages.validateForEnvelope(envelope, wireMessage);
        envelope.setPayload(java.util.Map.of("fixMessage", wireMessage));
      } else if ("COLLATERAL_INQUIRY_SUBMITTED".equals(envelope.getEventType())) {
        Object fixMessage = envelope.getPayload().get("fixMessage");
        if (!(fixMessage instanceof String wireMessage) || envelope.getPayload().size() != 1) {
          throw new IllegalStateException("INVALID_FIX_BB_OUTBOX_PAYLOAD");
        }
        fixMessages.validateCollateralInquiryForEnvelope(envelope, wireMessage);
      }
      receipt =
          transport.send(event.getKafkaTopic(), event.getPartitionKey(), codec.encode(envelope));
    } catch (RuntimeException failure) {
      store.failed(event, failure);
      return true;
    }
    // If the database fails or the process dies here, the lease recovers and delivery repeats.
    // Keep this outside the send catch: broker success is not a confirmed database commit.
    if (store.published(event, receipt)) {
      if ("LOAN_REQUEST_SUBMITTED".equals(event.getEventType())) {
        log.info(
            "FIX U1 loan request published: eventId={}, requestReference={}, topic={}, partition={}, offset={}",
            event.getEventId(),
            event.getCorrelationId(),
            event.getKafkaTopic(),
            receipt.getPartition(),
            receipt.getOffset());
      } else if ("COLLATERAL_INQUIRY_SUBMITTED".equals(event.getEventType())) {
        log.info(
            "FIX BB collateral inquiry published: eventId={}, inquiryId={}, topic={}, partition={}, offset={}",
            event.getEventId(),
            event.getCorrelationId(),
            event.getKafkaTopic(),
            receipt.getPartition(),
            receipt.getOffset());
      }
    }
    return true;
  }
}
