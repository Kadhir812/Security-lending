package com.hexaware.custodian_service.service.organization;

import com.hexaware.custodian_service.dto.request.OrganizationRequest;
import com.hexaware.custodian_service.dto.response.OrganizationResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.mapper.SetupMapper;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.SetupUsageRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

  private final OrganizationRepository organizations;
  private final SetupAccess access;
  private final SetupMapper mapper;
  private final Clock clock;
  private final SetupUsageRepository usage;

  @Transactional
  public OrganizationResponse create(
      Authentication authentication, OrganizationRequest request) {
    Organization actor = access.actor(authentication, true);
    SetupValidation.newVersion(request.getVersion());

    if (request.getOrganizationType() == OrganizationType.CUSTODIAN) {
      throw new AccessDeniedException(
          "New Custodian onboarding requires explicit platform provisioning; "
              + "no cross-Custodian grant exists");
    }

    var reference = SetupValidation.reference(request.getOrganizationReference());
    var name = SetupValidation.text(request.getOrganizationName(), "organizationName", 255);
    var existing = organizations.findLocked(reference);
    if (existing.isPresent()) {
      var organization = existing.get();
      boolean sameIdentity =
          organization.getOrganizationType() == request.getOrganizationType()
              && organization.getOrganizationName().equals(name)
              && organization.getStatus() == request.getStatus();
      if (!sameIdentity) {
        throw SetupException.conflict(
            "Shared organization already exists with different details; "
                + "use its registered identity");
      }
      return mapper.organization(organization);
    }

    Long userId = access.user(authentication).getUserId();
    var organization =
        Organization.builder()
            .organizationReference(reference)
            .organizationName(name)
            .organizationType(request.getOrganizationType())
            .status(request.getStatus())
            .registeredByCustodianId(actor.getOrganizationId())
            .createdAt(clock.instant())
            .createdByUserId(userId)
            .updatedAt(clock.instant())
            .updatedByUserId(userId)
            .build();
    return mapper.organization(organizations.saveAndFlush(organization));
  }

  @Transactional
  public OrganizationResponse update(
      Authentication authentication, String reference, OrganizationRequest request) {
    var actor = access.actor(authentication, true);
    var existing =
        access.visibleOrganization(
            SetupValidation.reference(reference), actor.getOrganizationId());
    access.managed(existing, actor.getOrganizationId());

    if (existing.getOrganizationType() == OrganizationType.CUSTODIAN) {
      throw new AccessDeniedException(
          "Custodian organizations are provisioned through the administrator-controlled setup "
              + "process");
    }

    var organization =
        organizations.findLocked(existing.getOrganizationReference()).orElseThrow();
    SetupValidation.version(organization.getVersion(), request.getVersion());
    if (usage.organizationShared(organization.getOrganizationId(), actor.getOrganizationId())) {
      throw SetupException.conflict(
          "Shared organization details require admin-controlled correction; "
              + "operators cannot change them");
    }

    boolean identityChanged =
        !organization
            .getOrganizationReference()
            .equals(SetupValidation.reference(request.getOrganizationReference()))
            || organization.getOrganizationType() != request.getOrganizationType();
    if (identityChanged) {
      throw SetupException.invalid("Organization reference and type are immutable");
    }

    organization.setOrganizationName(
        SetupValidation.text(request.getOrganizationName(), "organizationName", 255));
    organization.setStatus(request.getStatus());
    organization.setUpdatedAt(clock.instant());
    organization.setUpdatedByUserId(access.user(authentication).getUserId());
    organizations.flush();
    return mapper.organization(organization);
  }

  @Transactional(readOnly = true)
  public OrganizationResponse get(Authentication authentication, String reference) {
    var actor = access.actor(authentication, false);
    var organization =
        access.visibleOrganization(
            SetupValidation.reference(reference), actor.getOrganizationId());
    return mapper.organization(organization);
  }

  @Transactional(readOnly = true)
  public PageResponse<OrganizationResponse> list(
      Authentication authentication, int page, int size) {
    var actor = access.actor(authentication, false);
    var organizationsPage =
        organizations.findVisible(
            actor.getOrganizationId(), SetupValidation.page(page, size, "organizationReference"));
    return PageResponse.from(organizationsPage.map(mapper::organization));
  }
}
