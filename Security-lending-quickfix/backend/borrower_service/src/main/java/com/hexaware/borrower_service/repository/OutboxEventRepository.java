package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.OutboxEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  Optional<OutboxEvent> findByEventTypeAndCorrelationIdAndAggregateReference(
      String eventType, String correlationId, String aggregateReference);
}
