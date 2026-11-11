package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.OutboxEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  boolean existsByEventId(String eventId);
  Optional<OutboxEvent> findByEventId(String eventId);
  Optional<OutboxEvent> findByCorrelationIdAndEventType(String correlationId, String eventType);
  List<OutboxEvent> findByEventTypeAndAggregateTypeOrderByCreatedAtAsc(
      String eventType, String aggregateType);
}
