package com.hexaware.valuation_accural.repository;

import com.hexaware.valuation_accural.entity.InboxMessage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<InboxMessage> findByConsumerNameAndEventId(String consumerName, String eventId);
}
