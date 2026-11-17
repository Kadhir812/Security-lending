package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.dto.request.LocateRequestSubmission;
import com.hexaware.borrower_service.entity.LocateRequest;
import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import com.hexaware.borrower_service.service.LocateRequestService;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import com.hexaware.borrower_service.service.messaging.OutgoingEventService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class LocateRequestServiceTest {
  @Mock LocateRequestRepository locates;
  @Mock LoanRequestSubmissionValidator validator;
  @Mock OutgoingEventService outgoing;

  private LocateRequestService service;
  private LocalUserAuthentication authentication;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-08T05:30:00Z"), ZoneOffset.UTC);
    service = new LocateRequestService(locates, validator, outgoing, clock);

    Jwt token =
        Jwt.withTokenValue("signed-user-token")
            .header("alg", "RS256")
            .issuer("https://identity.example.test/realms/lending")
            .subject("borrower-user-42")
            .issuedAt(clock.instant())
            .expiresAt(clock.instant().plusSeconds(600))
            .audience(List.of("borrower-service"))
            .claim("organization_reference", "BORROWER-001")
            .build();

    authentication =
        new LocalUserAuthentication(
            token,
            List.of(new SimpleGrantedAuthority("ROLE_TRADER")),
            42L,
            "BORROWER-001");
  }

  @Test
  void returnsExistingLocateWhenDuplicateInsertHitsUniqueConstraint() {
    String locateReference = "LOCATE-REF-001";
    String mslaReference = "MSLA-2026-001";
    String isin = "US1234567890";
    var submission = new LocateRequestSubmission(mslaReference, isin, 100L);
    var existing =
        LocateRequest.builder()
            .borrowerOrganizationReference("BORROWER-001")
            .custodianOrganizationReference("CUSTODIAN-001")
            .submissionStatus("QUEUED")
            .decisionSourceVersion(0L)
            .version(0L)
            .locateId(42L)
            .locateReference(locateReference)
            .mslaReference(mslaReference)
            .securityIsin(isin)
            .quantity(100L)
            .createdBy(42L)
            .requestTime(Instant.parse("2026-11-08T05:30:00Z"))
            .status(LocateStatus.PENDING)
            .build();

    when(validator.requireBorrower(authentication)).thenReturn(authentication);
    when(locates.findByLocateReferenceAndBorrowerOrganizationReference(locateReference, "BORROWER-001"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existing));
    when(validator.validateLocate(authentication, mslaReference, isin))
        .thenReturn(new LoanRequestSubmissionValidator.ValidatedLocate(authentication, "CUSTODIAN-001"));
    when(locates.saveAndFlush(any(LocateRequest.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate locate_reference"));

    var response = service.submit(authentication, locateReference, submission);

    assertThat(response.locateReference()).isEqualTo(locateReference);
    assertThat(response.mslaReference()).isEqualTo(mslaReference);
    assertThat(response.securityIsin()).isEqualTo(isin);
  }
}
