package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  boolean existsByEventId(String eventId);
  List<OutboxEvent> findByEventTypeAndAggregateTypeOrderByCreatedAtAsc(
      String eventType, String aggregateType);
}
