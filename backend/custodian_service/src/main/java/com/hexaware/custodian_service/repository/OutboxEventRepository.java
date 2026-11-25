package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.enums.OutboxEventStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  boolean existsByEventId(String eventId);
  Optional<OutboxEvent> findByEventId(String eventId);
  Optional<OutboxEvent> findByCorrelationIdAndEventType(String correlationId, String eventType);
  List<OutboxEvent>
      findByEventTypeAndAggregateTypeAndAggregateReferenceOrderByAggregateVersionAscCreatedAtAsc(
          String eventType, String aggregateType, String aggregateReference);

  @Query(
      """
      select coalesce(max(outboxEvent.aggregateVersion), 0L)
      from OutboxEvent outboxEvent
      where outboxEvent.eventType = :eventType
        and outboxEvent.aggregateType = :aggregateType
        and outboxEvent.aggregateReference = :aggregateReference
      """)
  long findMaxAggregateVersion(
      @Param("eventType") String eventType,
      @Param("aggregateType") String aggregateType,
      @Param("aggregateReference") String aggregateReference);

  long countByStatus(OutboxEventStatus status);
}
