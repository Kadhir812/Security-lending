package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.MessageLog;
import com.hexaware.borrower_service.enums.MessageLogDirection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {
  Optional<MessageLog> findByServiceNameAndDirectionAndEventId(
      String service, MessageLogDirection direction, String eventId);
}
