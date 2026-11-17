package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.InboxMessage;
import com.hexaware.borrower_service.enums.InboxMessageStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<InboxMessage> findByConsumerNameAndEventId(String consumerName, String eventId);

  List<InboxMessage>
      findByConsumerNameAndEventTypeAndAggregateReferenceAndStatusOrderByReceivedAtAsc(
          String consumerName,
          String eventType,
          String aggregateReference,
          InboxMessageStatus status);
}
