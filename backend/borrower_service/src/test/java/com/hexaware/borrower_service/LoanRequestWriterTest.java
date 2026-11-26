package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.dto.request.LoanRequestSubmission;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.entity.LocateRequest;
import com.hexaware.borrower_service.entity.User;
import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.enums.UserStatus;
import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import com.hexaware.borrower_service.repository.UserRepository;
import com.hexaware.borrower_service.service.loan.LoanRequestWriter;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import com.hexaware.borrower_service.quickfix.LoanRequestFixMessageBuilder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanRequestWriterTest {
  @Mock LoanRequestRepository requests;
  @Mock LocateRequestRepository locates;
  @Mock UserRepository users;
  @Mock OutgoingEventService outgoing;

  @Test
  void persistsSubmittedIntentAndEnqueuesMatchingV1FixEvent() throws Exception {
    Clock clock =
        Clock.fixed(Instant.parse("2026-11-08T05:30:00Z"), ZoneOffset.UTC);
    LoanRequestWriter writer =
        new LoanRequestWriter(
            requests, locates, users, outgoing, new LoanRequestFixMessageBuilder(), clock);
    User user =
        User.builder()
            .userId(42L)
            .organizationReference("BORROWER-001")
            .status(UserStatus.ACTIVE)
            .build();
    when(users.findLockedByUserId(42L)).thenReturn(Optional.of(user));
    when(requests.findByRequestReference("LR-2026-000042")).thenReturn(Optional.empty());
    when(requests.saveAndFlush(any(LoanRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    var submission =
        new LoanRequestSubmission(
            "MSLA-2026-001",
            "INE002A01018",
            1000L,
            LocalDate.of(2026, 11, 10),
            "INR",
            null);

    LoanRequest saved =
        writer.submit(
            42L,
            "BORROWER-001",
            "https://identity.example.test/realms/lending",
            "borrower-user-42",
            "LR-2026-000042",
            submission,
            "CUSTODIAN-001",
            null);

    assertThat(saved.getSubmissionStatus()).hasToString("QUEUED");
    assertThat(saved.getStatus()).hasToString("PENDING");
    ArgumentCaptor<EventEnvelope> event = ArgumentCaptor.forClass(EventEnvelope.class);
    verify(outgoing).enqueue(eq("LOAN_REQUEST"), event.capture());
    assertThat(event.getValue().getEventType()).isEqualTo("LOAN_REQUEST_SUBMITTED");
    assertThat(event.getValue().getCorrelationId()).isEqualTo(saved.getRequestReference());
    assertThat(event.getValue().getAggregateReference()).isEqualTo(saved.getRequestReference());
    assertThat(event.getValue().getReceiverOrganizationReference()).isEqualTo("CUSTODIAN-001");
    assertThat(event.getValue().getPayload()).containsOnlyKeys("fixMessage");
    String wireMessage = (String) event.getValue().getPayload().get("fixMessage");
    var fixMessage = new LoanRequestFixMessageBuilder().parseAndValidate(wireMessage);
    new LoanRequestFixMessageBuilder().validateForEnvelope(event.getValue(), wireMessage);
    assertThat(fixMessage.getString(9001)).isEqualTo("LR-2026-000042");
    assertThat(fixMessage.getString(9002)).isEqualTo("MSLA-2026-001");
    assertThat(fixMessage.getString(48)).isEqualTo("INE002A01018");
    assertThat(fixMessage.getString(38)).isEqualTo("1000");
    assertThat(fixMessage.getString(15)).isEqualTo("INR");
    assertThat(fixMessage.isSetField(9005)).isFalse();
  }

  @Test
  void leavesApprovedLocateAvailableUntilLoanIsApproved() {
    Instant now = Instant.parse("2026-11-08T05:30:00Z");
    LoanRequestWriter writer =
        new LoanRequestWriter(
            requests,
            locates,
            users,
            outgoing,
            new LoanRequestFixMessageBuilder(),
            Clock.fixed(now, ZoneOffset.UTC));
    User user =
        User.builder()
            .userId(42L)
            .organizationReference("BORROWER-001")
            .status(UserStatus.ACTIVE)
            .build();
    LocateRequest locate =
        LocateRequest.builder()
            .locateId(77L)
            .locateReference("LOCATE-001")
            .borrowerOrganizationReference("BORROWER-001")
            .mslaReference("MSLA-2026-001")
            .securityIsin("INE002A01018")
            .approvedQuantity(1200L)
            .expiryTime(now.plusSeconds(3600))
            .status(LocateStatus.APPROVED)
            .build();
    when(users.findLockedByUserId(42L)).thenReturn(Optional.of(user));
    when(requests.findByRequestReference("LR-2026-000043")).thenReturn(Optional.empty());
    when(locates.findById(77L)).thenReturn(Optional.of(locate));
    when(requests.saveAndFlush(any(LoanRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    var submission =
        new LoanRequestSubmission(
            "MSLA-2026-001",
            "INE002A01018",
            1000L,
            LocalDate.of(2026, 11, 10),
            "INR",
            "LOCATE-001");

    LoanRequest saved =
        writer.submit(
            42L,
            "BORROWER-001",
            "https://identity.example.test/realms/lending",
            "borrower-user-42",
            "LR-2026-000043",
            submission,
            "CUSTODIAN-001",
            77L);

    assertThat(saved.getLocateId()).isEqualTo(77L);
    assertThat(locate.getStatus()).isEqualTo(LocateStatus.APPROVED);
  }
}
