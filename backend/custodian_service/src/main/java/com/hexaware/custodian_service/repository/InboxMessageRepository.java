package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.InboxMessage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import com.hexaware.custodian_service.enums.InboxMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<InboxMessage> findByConsumerNameAndEventId(String consumerName, String eventId);

  List<InboxMessage> findByConsumerNameAndEventTypeAndStatusOrderByReceivedAtDesc(
      String consumerName, String eventType, InboxMessageStatus status);
}
