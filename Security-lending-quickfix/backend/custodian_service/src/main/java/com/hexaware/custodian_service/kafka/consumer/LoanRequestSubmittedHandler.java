package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.client.SecurityReferenceResponse;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.LocateRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.LoanRequestStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.UserStatus;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.exception.RetryableMessageException;
import com.hexaware.custodian_service.exception.SetupException;
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
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanRequestSubmittedHandler implements MessageHandler {
  private final LoanRequestFixMessageParser fixMessages;
  private final LoanRequestRepository requests;
  private final LocateRequestRepository locates;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final UserRoleRepository roles;
  private final SecurityReferenceClient securities;
  private final Clock clock;

  @Value("${app.auth.issuer}")
  private String trustedIssuer;

  @Override
  public String eventType() {
    return "LOAN_REQUEST_SUBMITTED";
  }

  @Override
  public void handle(EventEnvelope event) {
    LoanRequestSubmittedPayload payload = payload(event);
    validateEnvelope(event, payload);
    var borrower = requireBorrower(payload);
    MslaAgreement agreement =
        requireAgreement(payload, borrower.getOrganization().getOrganizationId());
    var mandate =
        mandates
            .findById(agreement.getMandateId())
            .orElseThrow(() -> invalid("MSLA mandate is unavailable"));
    var lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .orElseThrow(() -> invalid("MSLA lender is unavailable"));
    var custodian =
        organizations
            .findById(mandate.getCustodianOrgId())
            .orElseThrow(() -> invalid("MSLA custodian is unavailable"));
    validateParties(payload, agreement, mandate, lender, custodian);
    validateDates(payload, agreement, mandate);
    SecurityReferenceResponse security = requireSecurity(payload.securityIsin());
    validateSecurity(payload, agreement, security);
    Long locateId = validateLocate(payload, agreement, security);

    LoanRequest existing = requests.findByRequestReference(payload.requestReference()).orElse(null);
    if (existing != null) {
      if (!matches(existing, payload, agreement, locateId)) {
        throw invalid("REQUEST_REFERENCE_REUSED_WITH_DIFFERENT_CONTENT");
      }
      return;
    }

    requests.saveAndFlush(
        LoanRequest.builder()
            .submittedByIssuer(payload.submittedByIssuer())
            .requestReference(payload.requestReference())
            .mslaId(agreement.getMslaId())
            .locateId(locateId)
            .securityIsin(payload.securityIsin())
            .quantity(payload.quantity())
            .requestedSettlementDate(payload.requestedSettlementDate())
            .settlementCurrency(payload.settlementCurrency())
            .submittedBySubject(payload.submittedBySubject())
            .requestedAt(event.getOccurredAt())
            .status(LoanRequestStatus.PENDING)
            .build());
  }

  private LoanRequestSubmittedPayload payload(EventEnvelope event) {
    try {
      LoanRequestSubmittedPayload payload = fixMessages.parse(event);
      if (payload == null) throw invalid("INVALID_LOAN_REQUEST_PAYLOAD");
      return payload;
    } catch (InvalidEnvelopeException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw invalid("INVALID_LOAN_REQUEST_PAYLOAD");
    }
  }

  private void validateEnvelope(EventEnvelope event, LoanRequestSubmittedPayload payload) {
    if (!"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || payload.payloadVersion() == null
        || payload.payloadVersion() != 1
        || !nonBlank(payload.requestReference())
        || !payload.requestReference().equals(event.getAggregateReference())
        || !payload.requestReference().equals(event.getCorrelationId())
        || !Objects.equals(
            payload.borrowerOrganizationReference(), event.getSenderOrganizationReference())
        || !Objects.equals(
            payload.custodianOrganizationReference(), event.getReceiverOrganizationReference())
        || event.getAggregateVersion() == null
        || event.getAggregateVersion() != 0L) {
      throw invalid("LOAN_REQUEST_ENVELOPE_MISMATCH");
    }
    if (!nonBlank(payload.mslaReference())
        || !nonBlank(payload.securityIsin())
        || !payload.securityIsin().matches("[A-Z0-9]{12}")
        || payload.quantity() == null
        || payload.quantity() <= 0
        || payload.requestedSettlementDate() == null
        || payload.settlementCurrency() == null
        || !payload.settlementCurrency().matches("[A-Z]{3}")
        || !nonBlank(payload.submittedByIssuer())
        || !nonBlank(payload.submittedBySubject())) {
      throw invalid("INVALID_LOAN_REQUEST_FIELDS");
    }
  }

  private com.hexaware.custodian_service.entity.User requireBorrower(
      LoanRequestSubmittedPayload payload) {
    if (!trustedIssuer.equals(payload.submittedByIssuer())) {
      throw invalid("UNTRUSTED_BORROWER_ISSUER");
    }
    var user =
        users
            .findByIdentityIssuerAndIdentitySubject(
                payload.submittedByIssuer(), payload.submittedBySubject())
            .orElseThrow(() -> invalid("BORROWER_MEMBERSHIP_NOT_FOUND"));
    if (user.getStatus() != UserStatus.ACTIVE
        || user.getOrganization().getStatus() != OrganizationStatus.ACTIVE
        || user.getOrganization().getOrganizationType() != OrganizationType.BORROWER
        || !user.getOrganization().getOrganizationReference()
            .equals(payload.borrowerOrganizationReference())
        || roles.findByUserUserId(user.getUserId()).stream()
            .noneMatch(role -> "TRADER".equals(role.getRole().getRoleName()))) {
      throw invalid("BORROWER_MEMBERSHIP_MISMATCH");
    }
    return user;
  }

  private MslaAgreement requireAgreement(LoanRequestSubmittedPayload payload, Long borrowerId) {
    MslaAgreement agreement =
        agreements
            .findByMslaReference(payload.mslaReference())
            .orElseThrow(() -> invalid("MSLA_NOT_FOUND"));
    if (!agreement.getBorrowerOrgId().equals(borrowerId)) {
      throw invalid("MSLA_NOT_OWNED_BY_BORROWER");
    }
    return agreement;
  }

  private void validateParties(
      LoanRequestSubmittedPayload payload,
      MslaAgreement agreement,
      com.hexaware.custodian_service.entity.LenderMandate mandate,
      com.hexaware.custodian_service.entity.Organization lender,
      com.hexaware.custodian_service.entity.Organization custodian) {
    if (agreement.getAgreementStatus() != AgreementStatus.ACTIVE
        || mandate.getStatus() != MandateStatus.ACTIVE
        || lender.getStatus() != OrganizationStatus.ACTIVE
        || lender.getOrganizationType() != OrganizationType.LENDER
        || custodian.getStatus() != OrganizationStatus.ACTIVE
        || custodian.getOrganizationType() != OrganizationType.CUSTODIAN
        || !custodian.getOrganizationReference()
            .equals(payload.custodianOrganizationReference())) {
      throw invalid("MSLA_PARTIES_OR_STATUS_INVALID");
    }
  }

  private void validateDates(
      LoanRequestSubmittedPayload payload,
      MslaAgreement agreement,
      com.hexaware.custodian_service.entity.LenderMandate mandate) {
    LocalDate today = LocalDate.now(clock);
    LocalDate settlementDate = payload.requestedSettlementDate();
    if (settlementDate.isBefore(today)
        || !covers(agreement.getEffectiveDate(), agreement.getExpiryDate(), today)
        || !covers(agreement.getEffectiveDate(), agreement.getExpiryDate(), settlementDate)
        || !covers(mandate.getEffectiveFrom(), mandate.getEffectiveTo(), today)
        || !covers(mandate.getEffectiveFrom(), mandate.getEffectiveTo(), settlementDate)) {
      throw invalid("MSLA_OR_MANDATE_NOT_VALID_FOR_REQUEST_DATES");
    }
  }

  private SecurityReferenceResponse requireSecurity(String isin) {
    try {
      return securities.require(isin);
    } catch (SetupException exception) {
      if (exception.getStatus().is5xxServerError()) throw new RetryableMessageException();
      throw invalid("SECURITY_REFERENCE_NOT_FOUND");
    }
  }

  private void validateSecurity(
      LoanRequestSubmittedPayload payload,
      MslaAgreement agreement,
      SecurityReferenceResponse security) {
    var eligible = agreement.getEligibleSecurities();
    if (!payload.securityIsin().equals(security.isin())
        || !"ACTIVE".equals(security.status())
        || eligible == null
        || eligible.getAllowedTypes() == null
        || !eligible.getAllowedTypes().contains(security.securityType())
        || !agreement.getBaseCurrency().equals(payload.settlementCurrency())) {
      throw invalid("SECURITY_OR_CURRENCY_NOT_ELIGIBLE");
    }
  }

  private Long validateLocate(
      LoanRequestSubmittedPayload payload,
      MslaAgreement agreement,
      SecurityReferenceResponse security) {
    if (payload.locateReference() == null) return null;
    LocateRequest locate =
        locates
            .findByLocateReference(payload.locateReference())
            .orElseThrow(() -> invalid("LOCATE_NOT_FOUND"));
    if (!locate.getMslaId().equals(agreement.getMslaId())
        || !locate.getSecurityIsin().equals(security.isin())
        || !locate.getSubmittedByIssuer().equals(payload.submittedByIssuer())
        || !locate.getSubmittedBySubject().equals(payload.submittedBySubject())
        || !"APPROVED".equals(locate.getStatus())
        || locate.getExpiryTime().isBefore(clock.instant())
        || locate.getApprovedQuantity() == null
        || locate.getApprovedQuantity() < payload.quantity()) {
      throw invalid("LOCATE_DOES_NOT_MATCH_LOAN_REQUEST");
    }
    return locate.getLocateId();
  }

  private boolean matches(
      LoanRequest existing,
      LoanRequestSubmittedPayload payload,
      MslaAgreement agreement,
      Long locateId) {
    return existing.getSubmittedByIssuer().equals(payload.submittedByIssuer())
        && existing.getSubmittedBySubject().equals(payload.submittedBySubject())
        && existing.getMslaId().equals(agreement.getMslaId())
        && Objects.equals(existing.getLocateId(), locateId)
        && existing.getSecurityIsin().equals(payload.securityIsin())
        && existing.getQuantity().equals(payload.quantity())
        && existing.getRequestedSettlementDate().equals(payload.requestedSettlementDate())
        && Objects.equals(existing.getSettlementCurrency(), payload.settlementCurrency());
  }

  private boolean covers(LocalDate from, LocalDate to, LocalDate date) {
    return from != null
        && !date.isBefore(from)
        && (to == null || !date.isAfter(to));
  }

  private boolean nonBlank(String value) {
    return value != null && !value.isBlank();
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
