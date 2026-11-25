package com.hexaware.custodian_service.config;

import com.hexaware.custodian_service.enums.InboxMessageStatus;
import com.hexaware.custodian_service.enums.OutboxEventStatus;
import com.hexaware.custodian_service.repository.InboxMessageRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MessagingMetricsConfiguration {
  private final MeterRegistry meterRegistry;
  private final OutboxEventRepository outbox;
  private final InboxMessageRepository inbox;

  @PostConstruct
  public void registerGauges() {
    for (OutboxEventStatus status : OutboxEventStatus.values()) {
      Gauge.builder("securities.messaging.outbox.events", outbox,
          repository -> repository.countByStatus(status))
          .tag("status", status.name())
          .description("Outbox events grouped by status")
          .register(meterRegistry);
    }

    for (InboxMessageStatus status : InboxMessageStatus.values()) {
      Gauge.builder("securities.messaging.inbox.events", inbox,
          repository -> repository.countByStatus(status))
          .tag("status", status.name())
          .description("Inbox events grouped by status")
          .register(meterRegistry);
    }
  }
}
