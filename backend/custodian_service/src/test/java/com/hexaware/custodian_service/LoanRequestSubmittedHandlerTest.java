package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.client.SecurityReferenceResponse;
import com.hexaware.custodian_service.dto.request.EligibleSecurities;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.Role;
import com.hexaware.custodian_service.entity.User;
import com.hexaware.custodian_service.entity.UserRole;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.AssetType;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.UserStatus;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.exception.RetryableMessageException;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.consumer.LoanRequestSubmittedHandler;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.kafka.event.LoanRequestSubmittedPayload;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.LocateRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.repository.UserRoleRepository;
import com.hexaware.custodian_service.quickfix.LoanRequestFixMessageParser;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LoanRequestSubmittedHandlerTest {
  private static final String ISSUER = "https://identity.example.test/realms/lending";
  private static final String ISIN = "INE002A01018";
  private static final Instant NOW = Instant.parse("2026-11-08T05:30:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

  @Mock ObjectMapper mapper;
  @Mock LoanRequestRepository requests;
  @Mock LocateRequestRepository locates;
  @Mock MslaAgreementRepository agreements;
  @Mock LenderMandateRepository mandates;
  @Mock OrganizationRepository organizations;
  @Mock UserRepository users;
  @Mock UserRoleRepository roles;
  @Mock SecurityReferenceClient securities;

  private LoanRequestSubmittedHandler handler;
  private LoanRequestSubmittedPayload payload;
  private EventEnvelope event;
  private MslaAgreement agreement;
  private LenderMandate mandate;
  private Organization lender;
  private Organization custodian;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    handler =
        new LoanRequestSubmittedHandler(
            new LoanRequestFixMessageParser(mapper),
            requests,
            locates,
            agreements,
            mandates,
            organizations,
            users,
            roles,
            securities,
            clock);
    ReflectionTestUtils.setField(handler, "trustedIssuer", ISSUER);

    Organization borrower =
        organization(10L, "BORROWER-001", OrganizationType.BORROWER);
    User borrowerUser =
        User.builder()
            .userId(42L)
            .identityIssuer(ISSUER)
            .identitySubject("borrower-user-42")
            .organization(borrower)
            .status(UserStatus.ACTIVE)
            .build();
    lenient().when(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "borrower-user-42"))
        .thenReturn(Optional.of(borrowerUser));
    lenient().when(roles.findByUserUserId(42L))
        .thenReturn(
            List.of(
                UserRole.builder().role(Role.builder().roleName("TRADER").build()).build()));

    lender = organization(40L, "LENDER-001", OrganizationType.LENDER);
    custodian = organization(50L, "CUSTODIAN-001", OrganizationType.CUSTODIAN);
    mandate =
        LenderMandate.builder()
            .mandateId(30L)
            .mandateReference("MANDATE-2026-001")
            .lenderOrgId(40L)
            .custodianOrgId(50L)
            .effectiveFrom(TODAY.minusDays(1))
            .effectiveTo(TODAY.plusDays(20))
            .status(MandateStatus.ACTIVE)
            .build();
    agreement =
        MslaAgreement.builder()
            .mslaId(20L)
            .mslaReference("MSLA-2026-001")
            .mandateId(30L)
            .borrowerOrgId(10L)
            .effectiveDate(TODAY.minusDays(1))
            .expiryDate(TODAY.plusDays(10))
            .agreementStatus(AgreementStatus.ACTIVE)
            .baseCurrency("INR")
            .eligibleSecurities(
                EligibleSecurities.builder().allowedTypes(List.of(AssetType.EQUITY)).build())
            .build();
    lenient().when(agreements.findByMslaReference("MSLA-2026-001"))
        .thenReturn(Optional.of(agreement));
    lenient().when(mandates.findById(30L)).thenReturn(Optional.of(mandate));
    lenient().when(organizations.findById(40L)).thenReturn(Optional.of(lender));
    lenient().when(organizations.findById(50L)).thenReturn(Optional.of(custodian));
    lenient().when(securities.require(ISIN))
        .thenReturn(
            new SecurityReferenceResponse(
                1L, 0L, ISIN, "SYM", "Security", AssetType.EQUITY, "CAD", "EXCHANGE", "ACTIVE"));
    lenient().when(requests.findByRequestReference("LR-2026-000042"))
        .thenReturn(Optional.empty());

    payload =
        new LoanRequestSubmittedPayload(
            1,
            "LR-2026-000042",
            "BORROWER-001",
            "CUSTODIAN-001",
            "MSLA-2026-001",
            null,
            ISIN,
            1000L,
            TODAY.plusDays(2),
            "INR",
            ISSUER,
            "borrower-user-42");
    lenient()
        .when(mapper.convertValue(anyMap(), eq(LoanRequestSubmittedPayload.class)))
        .thenReturn(payload);
    event =
        EventEnvelope.builder()
            .eventId("c70ab756-2e5e-45c8-a307-4b79eabf2921")
            .eventType("LOAN_REQUEST_SUBMITTED")
            .schemaVersion(1)
            .producerService("BORROWER")
            .destinationService("CUSTODIAN")
            .correlationId(payload.requestReference())
            .aggregateReference(payload.requestReference())
            .aggregateVersion(0L)
            .occurredAt(NOW)
            .senderOrganizationReference(payload.borrowerOrganizationReference())
            .receiverOrganizationReference(payload.custodianOrganizationReference())
            .payload(Map.of("payloadVersion", 1))
            .build();
  }

  @Test
  void validatesAndPersistsReceivedRequestWithoutApproval() {
    handler.handle(event);

    ArgumentCaptor<LoanRequest> saved = ArgumentCaptor.forClass(LoanRequest.class);
    verify(requests).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getMslaId()).isEqualTo(20L);
    assertThat(saved.getValue().getStatus()).hasToString("PENDING");
    assertThat(saved.getValue().getSettlementCurrency()).isEqualTo("INR");
    assertThat(saved.getValue().getSubmittedBySubject()).isEqualTo("borrower-user-42");
  }

  @Test
  void rejectsRequestRoutedToTheWrongCustodian() {
    custodian.setOrganizationReference("CUSTODIAN-OTHER");

    assertThatThrownBy(() -> handler.handle(event))
        .isInstanceOf(InvalidEnvelopeException.class)
        .hasMessageContaining("MSLA_PARTIES");

    verify(requests, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(LoanRequest.class));
  }

  @Test
  void rejectsSecurityNotAllowedByTheSelectedMsla() {
    when(securities.require(ISIN))
        .thenReturn(
            new SecurityReferenceResponse(
                1L,
                0L,
                ISIN,
                "SYM",
                "Security",
                AssetType.CORPORATE_BOND,
                "INR",
                "EXCHANGE",
                "ACTIVE"));

    assertThatThrownBy(() -> handler.handle(event))
        .isInstanceOf(InvalidEnvelopeException.class)
        .hasMessageContaining("SECURITY_OR_CURRENCY");

    verify(requests, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(LoanRequest.class));
  }

  @Test
  void retriesWhenSecurityReferenceServiceIsUnavailable() {
    when(securities.require(ISIN)).thenThrow(SetupException.unavailable("upstream unavailable"));

    assertThatThrownBy(() -> handler.handle(event))
        .isInstanceOf(RetryableMessageException.class);
  }

  @Test
  void duplicateRequestReferenceDoesNotCreateAnotherCustodianRecord() {
    when(requests.findByRequestReference("LR-2026-000042"))
        .thenReturn(
            Optional.of(
                LoanRequest.builder()
                    .submittedByIssuer(ISSUER)
                    .submittedBySubject("borrower-user-42")
                    .requestReference("LR-2026-000042")
                    .mslaId(20L)
                    .securityIsin(ISIN)
                    .quantity(1000L)
                    .requestedSettlementDate(TODAY.plusDays(2))
                    .settlementCurrency("INR")
                    .build()));

    handler.handle(event);

    verify(requests, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(LoanRequest.class));
  }

  @Test
  void rejectsMismatchedCorrelationIdentifier() {
    event.setCorrelationId("different-reference");

    assertThatThrownBy(() -> handler.handle(event))
        .isInstanceOf(InvalidEnvelopeException.class)
        .hasMessageContaining("ENVELOPE_MISMATCH");

    verifyNoRequestSaved();
  }

  private void verifyNoRequestSaved() {
    verify(requests, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(LoanRequest.class));
  }

  private Organization organization(Long id, String reference, OrganizationType type) {
    return Organization.builder()
        .organizationId(id)
        .organizationReference(reference)
        .organizationName(reference)
        .organizationType(type)
        .status(OrganizationStatus.ACTIVE)
        .build();
  }
}
