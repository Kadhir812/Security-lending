package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hexaware.borrower_service.client.BorrowerSecurityReference;
import com.hexaware.borrower_service.client.CustodianMslaClient;
import com.hexaware.borrower_service.client.CustodianLocateClient;
import com.hexaware.borrower_service.client.SecurityReferenceClient;
import com.hexaware.borrower_service.dto.request.LoanRequestSubmission;
import com.hexaware.borrower_service.dto.response.BorrowerEligibleMslaResponse;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.enums.LoanRequestStatus;
import com.hexaware.borrower_service.enums.SubmissionStatus;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import com.hexaware.borrower_service.service.loan.LoanRequestService;
import com.hexaware.borrower_service.service.loan.LoanRequestQueryService;
import com.hexaware.borrower_service.service.loan.LoanRequestResponseMapper;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import com.hexaware.borrower_service.service.loan.LoanRequestWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class LoanRequestServiceTest {
  private static final String ISSUER = "https://identity.example.test/realms/lending";
  private static final String ISIN = "INE002A01018";
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

  @Mock CustodianMslaClient custodianMslas;
  @Mock CustodianLocateClient custodianLocates;
  @Mock SecurityReferenceClient securities;
  @Mock LoanRequestRepository requests;
  @Mock LoanContractViewRepository contractViews;
  @Mock LocateRequestRepository locates;
  @Mock LoanRequestWriter writer;

  private LoanRequestService service;
  private LocalUserAuthentication authentication;
  private LoanRequestSubmission submission;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-08T05:30:00Z"), ZoneOffset.UTC);
    LoanRequestSubmissionValidator validator =
        new LoanRequestSubmissionValidator(custodianMslas, securities, locates, custodianLocates, clock);
    LoanRequestResponseMapper responseMapper = new LoanRequestResponseMapper(locates);
    service =
        new LoanRequestService(
            requests,
            writer,
            validator,
            responseMapper,
            new LoanRequestQueryService(requests, contractViews, validator, responseMapper));
    Jwt token =
        Jwt.withTokenValue("signed-user-token")
            .header("alg", "RS256")
            .issuer(ISSUER)
            .subject("borrower-user-42")
            .issuedAt(clock.instant())
            .expiresAt(clock.instant().plusSeconds(600))
            .audience(List.of("borrower-service", "custodian-service"))
            .claim("organization_reference", "BORROWER-001")
            .build();
    authentication =
        new LocalUserAuthentication(
            token,
            List.of(new SimpleGrantedAuthority("ROLE_TRADER")),
            42L,
            "BORROWER-001");
    submission =
        new LoanRequestSubmission(
            "MSLA-2026-001", ISIN, 1000L, TODAY.plusDays(2), "INR", null);
  }

  @Test
  void submitsEligibleRequestThroughTheIdempotentWriter() {
    when(requests.findByRequestReference("LR-2026-000042")).thenReturn(Optional.empty());
    when(custodianMslas.require(authentication, "MSLA-2026-001")).thenReturn(msla("BORROWER-001"));
    when(securities.require(ISIN))
        .thenReturn(new BorrowerSecurityReference(ISIN, "EQUITY", "INR", "ACTIVE"));
    when(writer.submit(
            eq(42L),
            eq("BORROWER-001"),
            eq(ISSUER),
            eq("borrower-user-42"),
            eq("LR-2026-000042"),
            eq(submission),
            eq("CUSTODIAN-001"),
            eq(null)))
        .thenReturn(
            request(
                "LR-2026-000042",
                "BORROWER-001",
                SubmissionStatus.QUEUED,
                LoanRequestStatus.PENDING));

    var response = service.submit(authentication, "LR-2026-000042", submission);

    assertThat(response.submissionStatus()).isEqualTo(SubmissionStatus.QUEUED);
    assertThat(response.status()).isEqualTo(LoanRequestStatus.PENDING);
    verify(writer)
        .submit(
            42L,
            "BORROWER-001",
            ISSUER,
            "borrower-user-42",
            "LR-2026-000042",
            submission,
            "CUSTODIAN-001",
            null);
  }

  @Test
  void rejectsMslaFromAnotherBorrowerBeforeSecurityLookup() {
    when(requests.findByRequestReference("LR-2026-000042")).thenReturn(Optional.empty());
    when(custodianMslas.require(authentication, "MSLA-2026-001")).thenReturn(msla("BORROWER-OTHER"));

    assertThatThrownBy(() -> service.submit(authentication, "LR-2026-000042", submission))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("not owned");

    verifyNoInteractions(securities, writer);
  }

  @Test
  void rejectsSecurityAssetTypeOutsideMslaEligibility() {
    when(requests.findByRequestReference("LR-2026-000042")).thenReturn(Optional.empty());
    when(custodianMslas.require(authentication, "MSLA-2026-001")).thenReturn(msla("BORROWER-001"));
    when(securities.require(ISIN))
        .thenReturn(new BorrowerSecurityReference(ISIN, "CORPORATE_BOND", "INR", "ACTIVE"));

    assertThatThrownBy(() -> service.submit(authentication, "LR-2026-000042", submission))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("asset type");

    verify(writer, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsSecurityCurrencyThatDiffersFromMslaAndSettlementCurrency() {
    when(requests.findByRequestReference("LR-2026-000042")).thenReturn(Optional.empty());
    when(custodianMslas.require(authentication, "MSLA-2026-001"))
        .thenReturn(msla("BORROWER-001"));
    when(securities.require(ISIN))
        .thenReturn(new BorrowerSecurityReference(ISIN, "EQUITY", "USD", "ACTIVE"));

    assertThatThrownBy(() -> service.submit(authentication, "LR-2026-000042", submission))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("currencies must match");

    verify(writer, never()).submit(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void idempotentReplayReturnsExistingRequestWithoutRepublishing() {
    LoanRequest existing =
        request(
            "LR-2026-000042",
            "BORROWER-001",
            SubmissionStatus.SENT,
            LoanRequestStatus.PENDING);
    when(requests.findByRequestReference("LR-2026-000042")).thenReturn(Optional.of(existing));

    var response = service.submit(authentication, "LR-2026-000042", submission);

    assertThat(response.submissionStatus()).isEqualTo(SubmissionStatus.SENT);
    verifyNoInteractions(custodianMslas, securities, writer);
  }

  @Test
  void rejectsIdempotencyKeyReusedWithDifferentContent() {
    LoanRequest existing =
        request(
            "LR-2026-000042",
            "BORROWER-001",
            SubmissionStatus.QUEUED,
            LoanRequestStatus.PENDING);
    when(requests.findByRequestReference("LR-2026-000042")).thenReturn(Optional.of(existing));
    LoanRequestSubmission changed =
        new LoanRequestSubmission(
            "MSLA-2026-001", ISIN, 2000L, TODAY.plusDays(2), "INR", null);

    assertThatThrownBy(() -> service.submit(authentication, "LR-2026-000042", changed))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("already used");

    verifyNoInteractions(custodianMslas, securities, writer);
  }

  @Test
  void doesNotAllowRetrievalAcrossBorrowerOrganizations() {
    when(requests.findByRequestReferenceAndBorrowerOrganizationReference(
            "LR-OTHER", "BORROWER-001"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(authentication, "LR-OTHER"))
        .isInstanceOf(BorrowerException.class)
        .hasMessageContaining("not found");
  }

  private BorrowerEligibleMslaResponse msla(String borrower) {
    return new BorrowerEligibleMslaResponse(
        "MSLA-2026-001",
        "MANDATE-2026-001",
        "LENDER-001",
        borrower,
        "CUSTODIAN-001",
        TODAY.minusDays(1),
        TODAY.plusDays(30),
        "INR",
        List.of("EQUITY"),
        Map.of("allowed_types", List.of("EQUITY")));
  }

  private LoanRequest request(
      String reference,
      String borrower,
      SubmissionStatus submissionStatus,
      LoanRequestStatus status) {
    return LoanRequest.builder()
        .requestReference(reference)
        .borrowerOrganizationReference(borrower)
        .custodianOrganizationReference("CUSTODIAN-001")
        .mslaReference("MSLA-2026-001")
        .securityIsin(ISIN)
        .quantity(1000L)
        .requestedSettlementDate(TODAY.plusDays(2))
        .settlementCurrency("INR")
        .submissionStatus(submissionStatus)
        .status(status)
        .requestedAt(Instant.parse("2026-11-08T05:30:00Z"))
        .build();
  }
}
