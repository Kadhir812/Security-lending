package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.client.SecurityReferenceResponse;
import com.hexaware.custodian_service.entity.LocateRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.UserStatus;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.exception.RetryableMessageException;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LocateRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.repository.UserRoleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocateRequestSubmittedHandler implements MessageHandler {
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
    return "LOCATE_REQUEST_SUBMITTED";
  }

  @Override
  public void handle(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String reference = text(payload.get("locateReference"));
    String mslaReference = text(payload.get("mslaReference"));
    String borrowerReference = text(payload.get("borrowerOrganizationReference"));
    String custodianReference = text(payload.get("custodianOrganizationReference"));
    String issuer = text(payload.get("submittedByIssuer"));
    String subject = text(payload.get("submittedBySubject"));
    String isin = text(payload.get("securityIsin"));
    long quantity = number(payload.get("quantity"));
    long payloadVersion = number(payload.get("payloadVersion"));

    if (reference == null
        || !reference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}")
        || mslaReference == null
        || borrowerReference == null
        || custodianReference == null
        || issuer == null
        || subject == null
        || isin == null
        || !isin.matches("[A-Z0-9]{12}")
        || quantity <= 0
        || payloadVersion != 1
        || !"LOCATE_REQUEST_SUBMITTED".equals(event.getEventType())
        || !"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || !Objects.equals(event.getCorrelationId(), reference)
        || !Objects.equals(event.getAggregateReference(), reference)
        || !Objects.equals(event.getAggregateVersion(), 0L)
        || !Objects.equals(event.getSenderOrganizationReference(), borrowerReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), custodianReference)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())) {
      throw invalid("INVALID_LOCATE_REQUEST_ENVELOPE");
    }

    var borrower = requireBorrower(issuer, subject, borrowerReference);
    MslaAgreement agreement =
        agreements.findByMslaReference(mslaReference)
            .orElseThrow(() -> invalid("MSLA_NOT_FOUND"));
    if (!agreement.getBorrowerOrgId().equals(borrower.getOrganization().getOrganizationId())) {
      throw invalid("MSLA_NOT_OWNED_BY_BORROWER");
    }
    var mandate =
        mandates.findById(agreement.getMandateId())
            .orElseThrow(() -> invalid("MSLA_MANDATE_NOT_FOUND"));
    var lender =
        organizations.findById(mandate.getLenderOrgId())
            .orElseThrow(() -> invalid("MSLA_LENDER_NOT_FOUND"));
    var custodian =
        organizations.findById(mandate.getCustodianOrgId())
            .orElseThrow(() -> invalid("MSLA_CUSTODIAN_NOT_FOUND"));
    LocalDate today = LocalDate.now(clock);
    if (agreement.getAgreementStatus() != AgreementStatus.ACTIVE
        || !covers(agreement.getEffectiveDate(), agreement.getExpiryDate(), today)
        || mandate.getStatus() != MandateStatus.ACTIVE
        || !covers(mandate.getEffectiveFrom(), mandate.getEffectiveTo(), today)
        || lender.getStatus() != OrganizationStatus.ACTIVE
        || lender.getOrganizationType() != OrganizationType.LENDER
        || custodian.getStatus() != OrganizationStatus.ACTIVE
        || custodian.getOrganizationType() != OrganizationType.CUSTODIAN
        || !custodian.getOrganizationReference().equals(custodianReference)) {
      throw invalid("LOCATE_MANDATE_OR_PARTIES_NOT_ACTIVE");
    }

    SecurityReferenceResponse security = requireSecurity(isin);
    var eligible = agreement.getEligibleSecurities();
    if (!isin.equals(security.isin())
        || !"ACTIVE".equals(security.status())
        || eligible == null
        || eligible.getAllowedTypes() == null
        || !eligible.getAllowedTypes().contains(security.securityType())
        || !Objects.equals(agreement.getBaseCurrency(), security.currency())) {
      throw invalid("LOCATE_SECURITY_NOT_ELIGIBLE");
    }

    LocateRequest existing = locates.findByLocateReference(reference).orElse(null);
    if (existing != null) {
      if (!existing.getSubmittedByIssuer().equals(issuer)
          || !existing.getSubmittedBySubject().equals(subject)
          || !existing.getMslaId().equals(agreement.getMslaId())
          || !existing.getSecurityIsin().equals(isin)
          || !existing.getQuantity().equals(quantity)) {
        throw invalid("LOCATE_REFERENCE_REUSED_WITH_DIFFERENT_CONTENT");
      }
      return;
    }

    locates.saveAndFlush(
        LocateRequest.builder()
            .submittedByIssuer(issuer)
            .submittedBySubject(subject)
            .version(0L)
            .locateReference(reference)
            .mslaId(agreement.getMslaId())
            .securityIsin(isin)
            .quantity(quantity)
            .requestTime(event.getOccurredAt())
            .status("PENDING")
            .build());
  }

  private com.hexaware.custodian_service.entity.User requireBorrower(
      String issuer, String subject, String borrowerReference) {
    if (!trustedIssuer.equals(issuer)) throw invalid("UNTRUSTED_BORROWER_ISSUER");
    var user =
        users.findByIdentityIssuerAndIdentitySubject(issuer, subject)
            .orElseThrow(() -> invalid("BORROWER_MEMBERSHIP_NOT_FOUND"));
    if (user.getStatus() != UserStatus.ACTIVE
        || user.getOrganization().getStatus() != OrganizationStatus.ACTIVE
        || user.getOrganization().getOrganizationType() != OrganizationType.BORROWER
        || !user.getOrganization().getOrganizationReference().equals(borrowerReference)
        || roles.findByUserUserId(user.getUserId()).stream()
            .noneMatch(role -> "TRADER".equals(role.getRole().getRoleName()))) {
      throw invalid("BORROWER_MEMBERSHIP_MISMATCH");
    }
    return user;
  }

  private SecurityReferenceResponse requireSecurity(String isin) {
    try {
      return securities.require(isin);
    } catch (SetupException exception) {
      if (exception.getStatus().is5xxServerError()) throw new RetryableMessageException();
      throw invalid("SECURITY_REFERENCE_NOT_FOUND");
    }
  }

  private boolean covers(LocalDate from, LocalDate to, LocalDate date) {
    return from != null && !date.isBefore(from) && (to == null || !date.isAfter(to));
  }

  private long number(Object value) {
    if (value instanceof Number number) return number.longValue();
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_LOCATE_REQUEST_NUMBER");
    }
  }

  private String text(Object value) {
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
