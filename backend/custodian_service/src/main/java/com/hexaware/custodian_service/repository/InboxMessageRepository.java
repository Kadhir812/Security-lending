package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.InboxMessage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import com.hexaware.custodian_service.enums.InboxMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<InboxMessage> findByConsumerNameAndEventId(String consumerName, String eventId);

  List<InboxMessage> findByConsumerNameAndEventTypeAndStatusOrderByReceivedAtDesc(
      String consumerName, String eventType, InboxMessageStatus status);

  @Query(
      value = """
          select * from inbox_message
          where consumer_name = :consumerName
            and event_type = 'COLLATERAL_INQUIRY_SUBMITTED'
            and status = 'PROCESSED'
            and JSON_UNQUOTE(JSON_EXTRACT(payload, '$.receiverOrganizationReference')) = :custodianReference
          order by received_at desc, inbox_id desc
          """,
      countQuery = """
          select count(*) from inbox_message
          where consumer_name = :consumerName
            and event_type = 'COLLATERAL_INQUIRY_SUBMITTED'
            and status = 'PROCESSED'
            and JSON_UNQUOTE(JSON_EXTRACT(payload, '$.receiverOrganizationReference')) = :custodianReference
          """,
      nativeQuery = true)
  Page<InboxMessage> findProcessedCollateralInquiries(
      @Param("consumerName") String consumerName,
      @Param("custodianReference") String custodianReference,
      Pageable pageable);

  long countByStatus(InboxMessageStatus status);
}
