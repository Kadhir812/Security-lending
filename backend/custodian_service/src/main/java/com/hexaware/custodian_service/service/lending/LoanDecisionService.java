package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.client.SecurityReferenceResponse;
import com.hexaware.custodian_service.client.ValuationCalculationClient;
import com.hexaware.custodian_service.client.ValuationCalculationRequest;
import com.hexaware.custodian_service.client.ValuationCalculationResponse;
import com.hexaware.custodian_service.dto.response.LoanRequestDecisionResponse;
import com.hexaware.custodian_service.entity.LendingFee;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.LoanRequestStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.CollateralHaircutRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.LendingFeeRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.fees.LendingFeePolicy;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanDecisionService {
  private final SetupAccess access;
  private final LoanRequestRepository requests;
  private final LoanContractRepository contracts;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final LendingFeeRepository fees;
  private final CollateralHaircutRepository haircuts;
  private final SecurityReferenceClient securities;
  private final LendingFeePolicy feePolicy;
  private final ValuationCalculationClient valuation;
  private final LoanDecisionCommitter committer;
  private final Clock clock;

  @Value("${app.approval.max-price-age:PT24H}")
  private Duration maxPriceAge;

  @PostConstruct
  void validatePriceAgeConfiguration() {
    if (maxPriceAge == null || maxPriceAge.isNegative() || maxPriceAge.isZero()) {
      throw new IllegalStateException("app.approval.max-price-age must be positive");
    }
  }

  public LoanRequestDecisionResponse approve(
      Authentication authentication, String requestReference) {
    var actor = access.actor(authentication, false);
    LoanRequest request = requireRequest(requestReference);
    var context = requireParties(request, actor.getOrganizationId());
    if (request.getStatus() == LoanRequestStatus.APPROVED) {
      return contracts
          .findByLoanRequestId(request.getRequestId())
          .map(contract -> committer.response(request, contract))
          .orElseThrow(() -> SetupException.conflict("Approved request has no contract"));
    }
    if (request.getStatus() != LoanRequestStatus.PENDING) {
      throw SetupException.conflict("Only pending requests can be approved");
    }
    validateAgreement(request, context.agreement(), context.mandate(), context.borrower(), context.lender());
    ReservationReviewRules.requireNotOverdue(
        request.getRequestedSettlementDate(), clock.instant());
    var security = securities.require(request.getSecurityIsin());
    if (!request.getSecurityIsin().equals(security.isin())
        || !"ACTIVE".equals(security.status())
        || !Objects.equals(request.getSettlementCurrency(), context.agreement().getBaseCurrency())
        || !Objects.equals(security.currency(), context.agreement().getBaseCurrency())) {
      throw SetupException.conflict("Request, security, and MSLA currencies/status are not eligible");
    }
    if (context.agreement().getCollateralType() != CollateralType.CASH) {
      throw SetupException.conflict("Only CASH collateral is supported");
    }
    if (context.agreement().getDefaultRules() != null
        && !context.agreement().getDefaultRules().isEmpty()) {
      throw SetupException.conflict("MSLA default_rules have no approved lending validator");
    }
    if (context.agreement().getEligibleSecurities() == null
        || context.agreement().getEligibleSecurities().getAllowedTypes() == null
        || !context.agreement()
            .getEligibleSecurities()
            .getAllowedTypes()
            .contains(security.securityType())) {
      throw SetupException.conflict("Security asset type is not eligible under this MSLA");
    }

    LocalDate tradeDate = LocalDate.now(clock);
    LendingFee fee =
        feePolicy.select(
            fees.findByMslaIdOrderByEffectiveFromDescFeeIdDesc(context.agreement().getMslaId()),
            request.getSecurityIsin(),
            tradeDate);
    var haircut =
        haircuts.findByMslaIdOrderByEffectiveFromDescHaircutIdDesc(
                context.agreement().getMslaId())
            .stream()
            .filter(rule -> rule.getCollateralType() == CollateralType.CASH)
            .filter(rule -> context.agreement().getBaseCurrency().equals(rule.getCurrency()))
            .filter(rule -> !rule.getEffectiveFrom().isAfter(clock.instant()))
            .filter(rule -> rule.getEffectiveTo() == null || rule.getEffectiveTo().isAfter(clock.instant()))
            .toList();
    if (haircut.size() != 1 || haircut.get(0).getHaircutPercentage().signum() != 0) {
      throw SetupException.conflict("Exactly one effective zero-percent CASH haircut is required");
    }

    if (!(authentication instanceof JwtAuthenticationToken jwt)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Authenticated Custodian token required for valuation");
    }
    ValuationCalculationResponse calculated =
        valuation.calculate(
            ValuationCalculationRequest.builder()
                .securityIsin(request.getSecurityIsin())
                .quantity(request.getQuantity())
                .coveragePercentage(context.agreement().getCollateralCoveragePct())
                .currency(request.getSettlementCurrency())
                .custodianOrganizationReference(actor.getOrganizationReference())
                .build(),
            jwt.getToken().getTokenValue());
    validateCalculation(request, context.agreement(), calculated);
    SecurityReferenceResponse validatedSecurity = securities.require(request.getSecurityIsin());
    if (!Objects.equals(security.version(), validatedSecurity.version())
        || !Objects.equals(security.isin(), validatedSecurity.isin())
        || !Objects.equals(security.status(), validatedSecurity.status())
        || !Objects.equals(security.securityType(), validatedSecurity.securityType())
        || !Objects.equals(security.currency(), validatedSecurity.currency())) {
      throw SetupException.conflict("Security reference changed while approval was being prepared");
    }
    return committer.approve(
        authentication,
        request.getRequestReference(),
        request.getVersion(),
        context.agreement().getVersion(),
        context.mandate().getVersion(),
        fee.getFeeId(),
        fee.getVersion(),
        haircut.get(0).getHaircutId(),
        haircut.get(0).getVersion(),
        validatedSecurity.securityType(),
        validatedSecurity.currency(),
        calculated);
  }

  public LoanRequestDecisionResponse reject(
      Authentication authentication, String requestReference, String reason) {
    String normalized = reason == null ? "" : reason.strip();
    if (normalized.isEmpty() || normalized.length() > 500) {
      throw SetupException.invalid("A rejection reason of 1 to 500 characters is required");
    }
    return committer.reject(authentication, requestReference, normalized);
  }

  private LoanRequest requireRequest(String reference) {
    return requests
        .findByRequestReference(reference)
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
  }

  private PartyContext requireParties(LoanRequest request, Long custodianId) {
    MslaAgreement agreement =
        agreements
            .findById(request.getMslaId())
            .orElseThrow(() -> SetupException.missing("Loan request MSLA not found"));
    LenderMandate mandate =
        mandates
            .findById(agreement.getMandateId())
            .orElseThrow(() -> SetupException.missing("Loan request mandate not found"));
    if (!mandate.getCustodianOrgId().equals(custodianId)) {
      throw SetupException.missing("Loan request not found in your Custodian scope");
    }
    var borrower =
        organizations
            .findById(agreement.getBorrowerOrgId())
            .orElseThrow(() -> SetupException.conflict("MSLA borrower is unavailable"));
    var lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .orElseThrow(() -> SetupException.conflict("MSLA lender is unavailable"));
    return new PartyContext(agreement, mandate, borrower, lender);
  }

  private void validateAgreement(
      LoanRequest request,
      MslaAgreement agreement,
      LenderMandate mandate,
      com.hexaware.custodian_service.entity.Organization borrower,
      com.hexaware.custodian_service.entity.Organization lender) {
    LocalDate today = LocalDate.now(clock);
    if (agreement.getAgreementStatus() != AgreementStatus.ACTIVE
        || mandate.getStatus() != MandateStatus.ACTIVE
        || borrower.getStatus() != OrganizationStatus.ACTIVE
        || borrower.getOrganizationType() != OrganizationType.BORROWER
        || lender.getStatus() != OrganizationStatus.ACTIVE
        || lender.getOrganizationType() != OrganizationType.LENDER
        || !SetupValidation.covers(agreement.getEffectiveDate(), agreement.getExpiryDate(), today)
        || !SetupValidation.covers(
            agreement.getEffectiveDate(), agreement.getExpiryDate(), request.getRequestedSettlementDate())
        || !SetupValidation.covers(mandate.getEffectiveFrom(), mandate.getEffectiveTo(), today)
        || !SetupValidation.covers(
            mandate.getEffectiveFrom(), mandate.getEffectiveTo(), request.getRequestedSettlementDate())) {
      throw SetupException.conflict("Request parties or agreement are no longer active and effective");
    }
  }

  private void validateCalculation(
      LoanRequest request, MslaAgreement agreement, ValuationCalculationResponse result) {
    LoanValuationRules.validate(
        request,
        agreement.getBaseCurrency(),
        agreement.getCollateralCoveragePct(),
        result,
        clock.instant(),
        maxPriceAge);
  }

  private record PartyContext(
      MslaAgreement agreement,
      LenderMandate mandate,
      com.hexaware.custodian_service.entity.Organization borrower,
      com.hexaware.custodian_service.entity.Organization lender) {}
}
