package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.SetupUsageRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.agreement.MslaService;
import com.hexaware.custodian_service.service.organization.MandateService;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Call inside the same transaction as any future lending decision or write. */
@Service
@RequiredArgsConstructor
public class LendingPrerequisiteService {

  private final SetupAccess access;
  private final OrganizationRepository organizations;
  private final MslaService mslas;
  private final MandateService mandates;
  private final MslaAgreementRepository agreements;
  private final SetupUsageRepository usage;
  private final Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public MslaAgreement requireActive(
      Authentication authentication, String reference, LocalDate businessDate) {
    var actor = access.actor(authentication, true);
    var existing = mslas.find(reference, actor.getOrganizationId());
    var view = mslas.get(authentication, reference);
    var mandate = mandates.lock(view.getMandateReference(), actor.getOrganizationId());
    var agreement = agreements.findLocked(existing.getMslaId()).orElseThrow();

    var lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .flatMap(
                organization ->
                    organizations.findLocked(organization.getOrganizationReference()))
            .orElseThrow();
    access.activeType(lender, OrganizationType.LENDER);

    var borrower =
        organizations
            .findById(agreement.getBorrowerOrgId())
            .flatMap(
                organization ->
                    organizations.findLocked(organization.getOrganizationReference()))
            .orElseThrow();
    access.activeType(borrower, OrganizationType.BORROWER);

    if (businessDate == null) {
      throw SetupException.invalid("businessDate is required");
    }

    LocalDate today = LocalDate.now(clock);
    boolean mandateEffective =
        mandate.getStatus() == MandateStatus.ACTIVE
            && SetupValidation.covers(
                mandate.getEffectiveFrom(), mandate.getEffectiveTo(), today)
            && SetupValidation.covers(
                mandate.getEffectiveFrom(), mandate.getEffectiveTo(), businessDate);
    boolean agreementEffective =
        agreement.getAgreementStatus() == AgreementStatus.ACTIVE
            && SetupValidation.covers(
                agreement.getEffectiveDate(), agreement.getExpiryDate(), today)
            && SetupValidation.covers(
                agreement.getEffectiveDate(), agreement.getExpiryDate(), businessDate);
    if (!mandateEffective || !agreementEffective) {
      throw SetupException.conflict("An active effective mandate and MSLA are required");
    }

    if (usage.hasNonZeroCashHaircut(agreement.getMslaId(), businessDate, businessDate)) {
      throw SetupException.conflict("Effective CASH collateral haircuts must be zero");
    }
    if (agreement.getDefaultRules() != null && !agreement.getDefaultRules().isEmpty()) {
      throw SetupException.conflict(
          "Agreement business rules require a defined validator before lending");
    }
    return agreement;
  }
}
