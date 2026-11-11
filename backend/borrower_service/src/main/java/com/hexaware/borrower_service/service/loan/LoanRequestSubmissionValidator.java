package com.hexaware.borrower_service.service.loan;

import com.hexaware.borrower_service.client.BorrowerSecurityReference;
import com.hexaware.borrower_service.client.CustodianMslaClient;
import com.hexaware.borrower_service.client.SecurityReferenceClient;
import com.hexaware.borrower_service.dto.request.LoanRequestSubmission;
import com.hexaware.borrower_service.dto.response.BorrowerEligibleMslaResponse;
import com.hexaware.borrower_service.entity.LocateRequest;
import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanRequestSubmissionValidator {
  private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}");
  private final CustodianMslaClient custodianMslas;
  private final SecurityReferenceClient securities;
  private final LocateRequestRepository locates;
  private final Clock clock;

  public List<BorrowerEligibleMslaResponse> eligibleMslaList(Authentication authentication) {
    return custodianMslas.eligible(requireBorrower(authentication));
  }

  public void validateIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || !REFERENCE.matcher(idempotencyKey).matches()) {
      throw BorrowerException.invalid("A valid Idempotency-Key is required");
    }
  }

  public ValidatedSubmission validate(
      Authentication authentication, LoanRequestSubmission submission) {
    LocalUserAuthentication borrower = requireBorrower(authentication);
    BorrowerEligibleMslaResponse msla =
        custodianMslas.require(authentication, submission.mslaReference());
    validateMsla(msla, borrower, submission);

    BorrowerSecurityReference security = securities.require(submission.securityIsin());
    validateSecurity(security, msla, submission);
    Long locateId = validateLocate(borrower, submission);
    return new ValidatedSubmission(borrower, msla.custodianOrganizationReference(), locateId);
  }

  public LocalUserAuthentication requireBorrower(Authentication authentication) {
    if (!(authentication instanceof LocalUserAuthentication user)
        || !user.isAuthenticated()
        || user.getAuthorities().stream()
            .noneMatch(authority -> authority.getAuthority().equals("ROLE_TRADER"))) {
      throw new AccessDeniedException("Authenticated borrower required");
    }
    return user;
  }

  private void validateMsla(
      BorrowerEligibleMslaResponse msla,
      LocalUserAuthentication user,
      LoanRequestSubmission submission) {
    LocalDate today = LocalDate.now(clock);
    if (!submission.mslaReference().equals(msla.mslaReference())
        || !user.getOrganizationReference().equals(msla.borrowerOrganizationReference())
        || msla.custodianOrganizationReference() == null
        || msla.custodianOrganizationReference().isBlank()) {
      throw BorrowerException.conflict("Selected MSLA is not owned by this borrower");
    }
    if (!"ACTIVE".equals(msla.agreementStatus())
        || !"ACTIVE".equals(msla.mandateStatus())) {
      throw BorrowerException.conflict("Selected MSLA or mandate is not active");
    }
    if (msla.effectiveDate() == null
        || msla.effectiveDate().isAfter(today)
        || (msla.expiryDate() != null
            && (msla.expiryDate().isBefore(today)
                || msla.expiryDate().isBefore(submission.requestedSettlementDate())))) {
      throw BorrowerException.conflict("Selected MSLA is not effective for the request dates");
    }
    if (submission.requestedSettlementDate().isBefore(today)
        || submission.requestedSettlementDate().isBefore(msla.effectiveDate())
        || msla.mandateEffectiveFrom() == null
        || submission.requestedSettlementDate().isBefore(msla.mandateEffectiveFrom())
        || (msla.mandateEffectiveTo() != null
            && submission.requestedSettlementDate().isAfter(msla.mandateEffectiveTo()))) {
      throw BorrowerException.invalid("Requested settlement date is not valid");
    }
    if (!msla.baseCurrency().equals(submission.settlementCurrency())) {
      throw BorrowerException.invalid("Settlement currency must match the selected MSLA");
    }
  }

  private void validateSecurity(
      BorrowerSecurityReference security,
      BorrowerEligibleMslaResponse msla,
      LoanRequestSubmission submission) {
    if (!submission.securityIsin().equals(security.isin())
        || !"ACTIVE".equals(security.status())) {
      throw BorrowerException.invalid("Security is not active or does not match the ISIN");
    }
    if (security.securityType() == null
        || msla.allowedAssetTypes() == null
        || !msla.allowedAssetTypes().contains(security.securityType())) {
      throw BorrowerException.invalid("Security asset type is not allowed by the MSLA");
    }
    if (!java.util.Objects.equals(security.currency(), submission.settlementCurrency())
        || !java.util.Objects.equals(security.currency(), msla.baseCurrency())) {
      throw BorrowerException.invalid("Security, settlement, and MSLA currencies must match");
    }
  }

  private Long validateLocate(
      LocalUserAuthentication user, LoanRequestSubmission submission) {
    if (submission.locateReference() == null) return null;
    LocateRequest locate =
        locates
            .findByLocateReferenceAndBorrowerOrganizationReference(
                submission.locateReference(), user.getOrganizationReference())
            .orElseThrow(() -> BorrowerException.invalid("Locate is not owned by this borrower"));
    if (!locate.getMslaReference().equals(submission.mslaReference())
        || !locate.getSecurityIsin().equals(submission.securityIsin())
        || locate.getStatus() != LocateStatus.APPROVED
        || locate.getExpiryTime().isBefore(clock.instant())
        || locate.getApprovedQuantity() == null
        || locate.getApprovedQuantity() < submission.quantity()) {
      throw BorrowerException.invalid("Locate does not match the loan request");
    }
    return locate.getLocateId();
  }

  public record ValidatedSubmission(
      LocalUserAuthentication borrower, String custodianOrganizationReference, Long locateId) {}
}
