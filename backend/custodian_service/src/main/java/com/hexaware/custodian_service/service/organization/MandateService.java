package com.hexaware.custodian_service.service.organization;

import com.hexaware.custodian_service.dto.request.MandateRequest;
import com.hexaware.custodian_service.dto.response.MandateResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.mapper.SetupMapper;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.SetupUsageRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MandateService {

  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final SetupAccess access;
  private final SetupMapper mapper;
  private final Clock clock;
  private final SetupUsageRepository usage;

  @Transactional
  public MandateResponse create(Authentication authentication, MandateRequest request) {
    var actor = access.actor(authentication, true);
    SetupValidation.newVersion(request.getVersion());

    var lender =
        organizations
            .findLocked(SetupValidation.reference(request.getLenderOrganizationReference()))
            .orElseThrow(() -> SetupException.missing("Lender organization not found"));
    access.activeType(lender, OrganizationType.LENDER);

    var userId = access.user(authentication).getUserId();
    var mandate =
        LenderMandate.builder()
            .mandateReference(SetupValidation.reference(request.getMandateReference()))
            .lenderOrgId(lender.getOrganizationId())
            .custodianOrgId(actor.getOrganizationId())
            .createdAt(clock.instant())
            .createdByUserId(userId)
            .build();

    apply(mandate, request, userId);
    mandates.saveAndFlush(mandate);
    return response(mandate);
  }

  @Transactional
  public MandateResponse update(
      Authentication authentication, String reference, MandateRequest request) {
    var actor = access.actor(authentication, true);
    var mandate = lock(reference, actor.getOrganizationId());
    SetupValidation.version(mandate.getVersion(), request.getVersion());

    var lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .flatMap(organization -> organizations.findLocked(organization.getOrganizationReference()))
            .orElseThrow();
    boolean relationshipChanged =
        !mandate
            .getMandateReference()
            .equals(SetupValidation.reference(request.getMandateReference()))
            || !lender
                .getOrganizationReference()
                .equals(SetupValidation.reference(request.getLenderOrganizationReference()));
    if (relationshipChanged) {
      throw SetupException.invalid("Mandate reference and parties are immutable");
    }

    if (request.getStatus() == MandateStatus.ACTIVE) {
      access.activeType(lender, OrganizationType.LENDER);
    }
    boolean datesChanged =
        !Objects.equals(mandate.getEffectiveFrom(), request.getEffectiveFrom())
            || !Objects.equals(mandate.getEffectiveTo(), request.getEffectiveTo());
    if (usage.mandateReferenced(mandate.getMandateId()) && datesChanged) {
      throw SetupException.conflict(
          "Referenced mandate dates are fixed; register a new mandate for renewal");
    }
    if (mandate.getStatus() == MandateStatus.EXPIRED
        && request.getStatus() != MandateStatus.EXPIRED) {
      throw SetupException.conflict("An expired mandate cannot be reactivated");
    }

    apply(mandate, request, access.user(authentication).getUserId());
    mandates.flush();
    return response(mandate);
  }

  private void apply(LenderMandate mandate, MandateRequest request, Long userId) {
    SetupValidation.dates(request.getEffectiveFrom(), request.getEffectiveTo());
    if (request.getStatus() == MandateStatus.EXPIRED
        && (request.getEffectiveTo() == null
            || !request.getEffectiveTo().isBefore(LocalDate.now(clock)))) {
      throw SetupException.invalid("EXPIRED requires an expiry date before today");
    }
    if (usage.duplicateMandate(
        mandate.getLenderOrgId(),
        mandate.getCustodianOrgId(),
        request.getEffectiveFrom(),
        request.getEffectiveTo(),
        mandate.getMandateId())) {
      throw SetupException.conflict("A mandate already exists for these parties and dates");
    }

    mandate.setEffectiveFrom(request.getEffectiveFrom());
    mandate.setEffectiveTo(request.getEffectiveTo());
    mandate.setStatus(request.getStatus());
    mandate.setUpdatedAt(clock.instant());
    mandate.setUpdatedByUserId(userId);
  }

  public LenderMandate lock(String reference, Long custodian) {
    return mandates
        .findLocked(SetupValidation.reference(reference), custodian)
        .orElseThrow(() -> SetupException.missing("Mandate not found in your scope"));
  }

  public Map<Long, Organization> parties(List<Long> ids) {
    return organizations.findAllById(ids).stream()
        .collect(Collectors.toMap(Organization::getOrganizationId, organization -> organization));
  }

  public MandateResponse response(LenderMandate mandate) {
    var parties =
        parties(List.of(mandate.getLenderOrgId(), mandate.getCustodianOrgId()));
    return mapper.mandate(mandate, parties, LocalDate.now(clock));
  }

  @Transactional(readOnly = true)
  public MandateResponse get(Authentication authentication, String reference) {
    var actor = access.actor(authentication, false);
    var mandate =
        mandates
            .findByMandateReferenceAndCustodianOrgId(
                SetupValidation.reference(reference), actor.getOrganizationId())
            .orElseThrow(() -> SetupException.missing("Mandate not found in your scope"));
    return response(mandate);
  }

  @Transactional(readOnly = true)
  public PageResponse<MandateResponse> list(
      Authentication authentication, int page, int size) {
    var actor = access.actor(authentication, false);
    var mandatesPage =
        mandates.findAll(
            (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("custodianOrgId"), actor.getOrganizationId()),
            SetupValidation.page(page, size, "mandateReference"));

    var organizationIds =
        mandatesPage.getContent().stream()
            .flatMap(
                mandate ->
                    List.of(mandate.getLenderOrgId(), mandate.getCustodianOrgId()).stream())
            .distinct()
            .toList();
    var organizationsById = parties(organizationIds);

    return PageResponse.from(
        mandatesPage.map(
            mandate ->
                mapper.mandate(mandate, organizationsById, LocalDate.now(clock))));
  }
}
