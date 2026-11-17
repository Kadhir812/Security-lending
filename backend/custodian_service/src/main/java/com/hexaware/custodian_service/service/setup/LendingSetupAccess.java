package com.hexaware.custodian_service.service.setup;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.client.SecurityReferenceResponse;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.MandateAccessRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LendingSetupAccess {

  private final SetupAccess access;
  private final OrganizationRepository organizations;
  private final MandateAccessRepository mandates;
  private final MslaAgreementRepository agreements;
  private final SecurityReferenceClient securities;
  private final Clock clock;

  public Organization actor(Authentication authentication, boolean write) {
    return access.actor(authentication, write);
  }

  public Long actorUserId(Authentication authentication) {
    return access.user(authentication).getUserId();
  }

  public Organization requireLender(
      Authentication authentication, String lenderReference, boolean write) {
    Organization actor = access.actor(authentication, write);
    String reference = SetupValidation.reference(lenderReference);
    Organization lender =
        (write
                ? organizations.findLocked(reference)
                : organizations.findByOrganizationReference(reference))
            .orElseThrow(() -> SetupException.missing("Lender not found in your mandate scope"));
    if (lender.getOrganizationType() != OrganizationType.LENDER
        || !mandates.hasCurrentLenderMandate(
            reference, actor.getOrganizationId(), LocalDate.now(clock), write)) {
      throw SetupException.missing("Lender not found in your mandate scope");
    }
    if (lender.getStatus() != com.hexaware.custodian_service.enums.OrganizationStatus.ACTIVE) {
      throw SetupException.conflict("An active lender organization is required");
    }
    return lender;
  }

  public MslaAgreement requireMsla(
      Authentication authentication, String mslaReference, boolean write) {
    Organization actor = access.actor(authentication, write);
    String reference = SetupValidation.reference(mslaReference);
    return (write
            ? agreements.findScopedLocked(reference, actor.getOrganizationId())
            : agreements.findScoped(reference, actor.getOrganizationId()))
        .orElseThrow(() -> SetupException.missing("MSLA not found in your Custodian scope"));
  }

  public SecurityReferenceResponse requireSecurity(String value) {
    String isin = normalizeIsin(value);
    SecurityReferenceResponse security = securities.require(isin);
    if (!isin.equals(security.isin())) {
      throw SetupException.unavailable(
          "Security reference response does not match the requested ISIN");
    }
    if (!"ACTIVE".equals(security.status())) {
      throw SetupException.conflict("Security must be active");
    }
    return security;
  }

  public String normalizeIsin(String value) {
    String isin = value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    if (isin == null || !isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]")) {
      throw SetupException.invalid("securityIsin must be a valid 12-character ISIN");
    }
    return isin;
  }

  public void requireEligible(MslaAgreement agreement, SecurityReferenceResponse security) {
    if (agreement.getEligibleSecurities() == null
        || agreement.getEligibleSecurities().getAllowedTypes() == null
        || !agreement
            .getEligibleSecurities()
            .getAllowedTypes()
            .contains(security.securityType())) {
      throw SetupException.invalid("Security asset type is not eligible under this MSLA");
    }
  }
}
