package com.hexaware.custodian_service.security.authorization;

import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authentication.LocalUserAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Keeps setup operations scoped to the authenticated Custodian organization. */
@Component
@RequiredArgsConstructor
public class SetupAccess {

  private final OrganizationAuthorization authorization;
  private final OrganizationRepository organizations;
  private final JdbcTemplate jdbc;

  public LocalUserAuthentication user(Authentication authentication) {
    if (!(authentication instanceof LocalUserAuthentication user)
        || !user.isAuthenticated()) {
      throw new AccessDeniedException("Local identity required");
    }
    return user;
  }

  public Organization actor(Authentication authentication, boolean write) {
    var user = user(authentication);
    authorization.requireRole(authentication, "CUSTODIAN_OPERATOR");

    var organization =
        (write
                ? organizations.findLocked(user.getOrganizationReference())
                : organizations.findByOrganizationReference(user.getOrganizationReference()))
            .orElseThrow(() -> new AccessDeniedException("Organization access denied"));

    boolean activeCustodian =
        organization.getOrganizationReference().equals(user.getOrganizationReference())
            && organization.getStatus() == OrganizationStatus.ACTIVE
            && organization.getOrganizationType() == OrganizationType.CUSTODIAN;
    if (!activeCustodian) {
      throw new AccessDeniedException("Active Custodian required");
    }
    return organization;
  }

  public boolean visible(Long organizationId, Long custodianId) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM organization org
            WHERE org.organization_id = ?
              AND (
                org.organization_id = ?
                OR org.registered_by_custodian_id = ?
                OR EXISTS (
                  SELECT 1
                  FROM lender_custodian_mandate mandate
                  WHERE mandate.lender_org_id = org.organization_id
                    AND mandate.custodian_org_id = ?
                )
                OR EXISTS (
                  SELECT 1
                  FROM msla_agreement agreement
                  JOIN lender_custodian_mandate mandate
                    ON agreement.mandate_id = mandate.mandate_id
                  WHERE agreement.borrower_org_id = org.organization_id
                    AND mandate.custodian_org_id = ?
                )
              )
            """,
            Integer.class,
            organizationId,
            custodianId,
            custodianId,
            custodianId,
            custodianId);
    return count != null && count > 0;
  }

  public Organization visibleOrganization(String reference, Long custodianId) {
    var organization =
        organizations
            .findByOrganizationReference(reference)
            .orElseThrow(() -> SetupException.missing("Organization not found in your scope"));
    if (!visible(organization.getOrganizationId(), custodianId)) {
      throw SetupException.missing("Organization not found in your scope");
    }
    return organization;
  }

  public void managed(Organization organization, Long custodianId) {
    boolean isCustodian = organization.getOrganizationId().equals(custodianId);
    boolean registeredByCustodian =
        organization.getOrganizationType() != OrganizationType.CUSTODIAN
            && custodianId.equals(organization.getRegisteredByCustodianId());
    if (!isCustodian && !registeredByCustodian) {
      throw new AccessDeniedException("Organization is not managed by your Custodian");
    }
  }

  public void activeType(Organization organization, OrganizationType requiredType) {
    if (organization.getOrganizationType() != requiredType
        || organization.getStatus() != OrganizationStatus.ACTIVE) {
      throw SetupException.conflict(
          "Relationship requires an active " + requiredType + " organization");
    }
  }
}
