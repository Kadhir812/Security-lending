package com.hexaware.custodian_service.mapper;

import com.hexaware.custodian_service.dto.response.MandateResponse;
import com.hexaware.custodian_service.dto.response.MslaResponse;
import com.hexaware.custodian_service.dto.response.OrganizationResponse;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SetupMapper {

  public OrganizationResponse organization(Organization organization) {
    return OrganizationResponse.builder()
        .organizationReference(organization.getOrganizationReference())
        .organizationName(organization.getOrganizationName())
        .organizationType(organization.getOrganizationType())
        .status(organization.getStatus())
        .version(organization.getVersion())
        .createdAt(organization.getCreatedAt())
        .createdByUserId(organization.getCreatedByUserId())
        .updatedAt(organization.getUpdatedAt())
        .updatedByUserId(organization.getUpdatedByUserId())
        .build();
  }

  public boolean mandateEffective(
      LenderMandate mandate, Map<Long, Organization> organizations, LocalDate date) {
    return mandate.getStatus() == MandateStatus.ACTIVE
        && SetupValidation.covers(
            mandate.getEffectiveFrom(), mandate.getEffectiveTo(), date)
        && organizations.get(mandate.getLenderOrgId()).getStatus() == OrganizationStatus.ACTIVE
        && organizations.get(mandate.getCustodianOrgId()).getStatus() == OrganizationStatus.ACTIVE;
  }

  public MandateResponse mandate(
      LenderMandate mandate, Map<Long, Organization> organizations, LocalDate date) {
    return MandateResponse.builder()
        .mandateReference(mandate.getMandateReference())
        .lenderOrganizationReference(
            organizations.get(mandate.getLenderOrgId()).getOrganizationReference())
        .custodianOrganizationReference(
            organizations.get(mandate.getCustodianOrgId()).getOrganizationReference())
        .effectiveFrom(mandate.getEffectiveFrom())
        .effectiveTo(mandate.getEffectiveTo())
        .status(mandate.getStatus())
        .currentlyEffective(mandateEffective(mandate, organizations, date))
        .version(mandate.getVersion())
        .createdAt(mandate.getCreatedAt())
        .createdByUserId(mandate.getCreatedByUserId())
        .updatedAt(mandate.getUpdatedAt())
        .updatedByUserId(mandate.getUpdatedByUserId())
        .build();
  }

  public MslaResponse msla(
      MslaAgreement agreement,
      LenderMandate mandate,
      Map<Long, Organization> organizations,
      LocalDate date) {
    boolean currentlyEffective =
        agreement.getAgreementStatus() == AgreementStatus.ACTIVE
            && SetupValidation.covers(
                agreement.getEffectiveDate(), agreement.getExpiryDate(), date)
            && mandateEffective(mandate, organizations, date)
            && organizations.get(agreement.getBorrowerOrgId()).getStatus()
                == OrganizationStatus.ACTIVE;

    return MslaResponse.builder()
        .mslaReference(agreement.getMslaReference())
        .mandateReference(mandate.getMandateReference())
        .lenderOrganizationReference(
            organizations.get(mandate.getLenderOrgId()).getOrganizationReference())
        .borrowerOrganizationReference(
            organizations.get(agreement.getBorrowerOrgId()).getOrganizationReference())
        .custodianOrganizationReference(
            organizations.get(mandate.getCustodianOrgId()).getOrganizationReference())
        .effectiveDate(agreement.getEffectiveDate())
        .expiryDate(agreement.getExpiryDate())
        .agreementStatus(agreement.getAgreementStatus())
        .currentlyEffective(currentlyEffective)
        .baseCurrency(agreement.getBaseCurrency())
        .eligibleSecurities(agreement.getEligibleSecurities())
        .collateralType(agreement.getCollateralType())
        .collateralCoveragePct(agreement.getCollateralCoveragePct())
        .mtmIntervalMinutes(agreement.getMtmIntervalMinutes())
        .marginCallWindowHours(agreement.getMarginCallWindowHours())
        .recallRule(agreement.getRecallRule())
        .defaultRules(agreement.getDefaultRules())
        .settlementCalendarCode(agreement.getSettlementCalendarCode())
        .version(agreement.getVersion())
        .createdAt(agreement.getCreatedAt())
        .createdByUserId(agreement.getCreatedByUserId())
        .updatedAt(agreement.getUpdatedAt())
        .updatedByUserId(agreement.getUpdatedByUserId())
        .build();
  }
}
