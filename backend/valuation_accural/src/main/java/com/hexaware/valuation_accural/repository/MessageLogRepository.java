package com.hexaware.valuation_accural.repository;

import com.hexaware.valuation_accural.entity.MessageLog;
import com.hexaware.valuation_accural.enums.MessageLogDirection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {
  Optional<MessageLog> findByServiceNameAndDirectionAndEventId(
      String service, MessageLogDirection direction, String eventId);
}
