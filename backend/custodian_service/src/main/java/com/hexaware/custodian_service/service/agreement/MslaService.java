package com.hexaware.custodian_service.service.agreement;

import com.hexaware.custodian_service.dto.request.MslaRequest;
import com.hexaware.custodian_service.dto.response.MslaResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.mapper.SetupMapper;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.SetupUsageRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.organization.MandateService;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MslaService {

  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandateRepository;
  private final OrganizationRepository organizations;
  private final SetupUsageRepository usage;
  private final MandateService mandates;
  private final SetupAccess access;
  private final SetupMapper mapper;
  private final AgreementRules rules;
  private final Clock clock;

  @Transactional
  public MslaResponse create(Authentication authentication, MslaRequest request) {
    var actor = access.actor(authentication, true);
    SetupValidation.newVersion(request.getVersion());
    rules.validate(request);
    if (request.getAgreementStatus() != AgreementStatus.DRAFT) {
      throw SetupException.invalid("Create an MSLA as DRAFT, then explicitly activate it");
    }

    var mandate = mandates.lock(request.getMandateReference(), actor.getOrganizationId());
    var borrower =
        organizations
            .findLocked(SetupValidation.reference(request.getBorrowerOrganizationReference()))
            .orElseThrow(() -> SetupException.missing("Borrower organization not found"));
    access.activeType(borrower, OrganizationType.BORROWER);

    var lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .flatMap(
                organization ->
                    organizations.findLocked(organization.getOrganizationReference()))
            .orElseThrow();
    access.activeType(lender, OrganizationType.LENDER);

    if (usage.duplicateAgreement(
        mandate.getMandateId(),
        borrower.getOrganizationId(),
        request.getEffectiveDate(),
        request.getExpiryDate(),
        null)) {
      throw SetupException.conflict("An agreement already exists for these parties and dates");
    }

    var userId = access.user(authentication).getUserId();
    var agreement =
        MslaAgreement.builder()
            .mslaReference(SetupValidation.reference(request.getMslaReference()))
            .mandateId(mandate.getMandateId())
            .borrowerOrgId(borrower.getOrganizationId())
            .createdAt(clock.instant())
            .createdByUserId(userId)
            .build();
    apply(agreement, request, userId);

    agreements.saveAndFlush(agreement);
    return response(agreement, mandate);
  }

  @Transactional
  public MslaResponse update(
      Authentication authentication, String reference, MslaRequest request) {
    var actor = access.actor(authentication, true);
    rules.validate(request);

    var existing = find(reference, actor.getOrganizationId());
    var mandate =
        mandateRepository
            .findById(existing.getMandateId())
            .map(
                foundMandate ->
                    mandates.lock(
                        foundMandate.getMandateReference(), actor.getOrganizationId()))
            .orElseThrow();
    var agreement = agreements.findLocked(existing.getMslaId()).orElseThrow();
    SetupValidation.version(agreement.getVersion(), request.getVersion());

    var borrower =
        organizations
            .findById(agreement.getBorrowerOrgId())
            .flatMap(
                organization ->
                    organizations.findLocked(organization.getOrganizationReference()))
            .orElseThrow();
    boolean relationshipChanged =
        !agreement
            .getMslaReference()
            .equals(SetupValidation.reference(request.getMslaReference()))
            || !mandate
                .getMandateReference()
                .equals(SetupValidation.reference(request.getMandateReference()))
            || !borrower
                .getOrganizationReference()
                .equals(SetupValidation.reference(request.getBorrowerOrganizationReference()));
    if (relationshipChanged) {
      throw SetupException.invalid("Agreement reference and relationships are immutable");
    }

    rules.transition(
        agreement.getAgreementStatus(),
        request.getAgreementStatus(),
        request.getExpiryDate(),
        LocalDate.now(clock));
    boolean changedTerms = termsChanged(agreement, request);
    if (agreement.getAgreementStatus() != AgreementStatus.DRAFT && changedTerms) {
      throw SetupException.conflict(
          "Fixed terms may only be edited while DRAFT; create a new agreement for amendments");
    }

    boolean used = usage.agreementUsed(agreement.getMslaId());
    if (used && changedTerms) {
      throw SetupException.conflict(
          "Agreement terms are already referenced by lending setup or workflows");
    }
    if (used
        && request.getAgreementStatus() != agreement.getAgreementStatus()
        && request.getAgreementStatus() != AgreementStatus.ACTIVE) {
      throw SetupException.conflict(
          "A used agreement cannot be deactivated or expired while lending records reference it");
    }
    if (usage.duplicateAgreement(
        agreement.getMandateId(),
        agreement.getBorrowerOrgId(),
        request.getEffectiveDate(),
        request.getExpiryDate(),
        agreement.getMslaId())) {
      throw SetupException.conflict("An agreement already exists for these parties and dates");
    }

    Long userId = access.user(authentication).getUserId();
    apply(agreement, request, userId);
    if (agreement.getAgreementStatus() == AgreementStatus.ACTIVE) {
      validateActivation(agreement, mandate, borrower);
    }

    agreements.flush();
    return response(agreement, mandate);
  }

  private void validateActivation(
      MslaAgreement agreement, LenderMandate mandate, Organization borrower) {
    access.activeType(borrower, OrganizationType.BORROWER);
    var lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .flatMap(
                organization ->
                    organizations.findLocked(organization.getOrganizationReference()))
            .orElseThrow();
    access.activeType(lender, OrganizationType.LENDER);
    rules.activate(agreement, mandate, LocalDate.now(clock));
    if (usage.hasNonZeroCashHaircut(
        agreement.getMslaId(), agreement.getEffectiveDate(), agreement.getExpiryDate())) {
      throw SetupException.conflict("Effective CASH collateral haircuts must be zero");
    }
  }

  private boolean termsChanged(MslaAgreement agreement, MslaRequest request) {
    return !Objects.equals(agreement.getEffectiveDate(), request.getEffectiveDate())
        || !Objects.equals(agreement.getExpiryDate(), request.getExpiryDate())
        || !agreement
            .getBaseCurrency()
            .equals(SetupValidation.currency(request.getBaseCurrency()))
        || !Objects.equals(agreement.getEligibleSecurities(), request.getEligibleSecurities())
        || agreement.getCollateralType() != request.getCollateralType()
        || agreement
                .getCollateralCoveragePct()
                .compareTo(request.getCollateralCoveragePct())
            != 0
        || !Objects.equals(agreement.getMtmIntervalMinutes(), request.getMtmIntervalMinutes())
        || !Objects.equals(
            agreement.getMarginCallWindowHours(), request.getMarginCallWindowHours())
        || !Objects.equals(agreement.getRecallRule(), request.getRecallRule())
        || !Objects.equals(agreement.getDefaultRules(), request.getDefaultRules())
        || !agreement
            .getSettlementCalendarCode()
            .equals(SetupValidation.reference(request.getSettlementCalendarCode()));
  }

  private void apply(MslaAgreement agreement, MslaRequest request, Long userId) {
    agreement.setEffectiveDate(request.getEffectiveDate());
    agreement.setExpiryDate(request.getExpiryDate());
    agreement.setAgreementStatus(request.getAgreementStatus());
    agreement.setBaseCurrency(SetupValidation.currency(request.getBaseCurrency()));
    agreement.setEligibleSecurities(request.getEligibleSecurities());
    agreement.setCollateralType(request.getCollateralType());
    agreement.setCollateralCoveragePct(request.getCollateralCoveragePct());
    agreement.setMtmIntervalMinutes(request.getMtmIntervalMinutes());
    agreement.setMarginCallWindowHours(request.getMarginCallWindowHours());
    agreement.setRecallRule(request.getRecallRule());
    agreement.setDefaultRules(request.getDefaultRules());
    agreement.setSettlementCalendarCode(
        SetupValidation.reference(request.getSettlementCalendarCode()));
    agreement.setUpdatedAt(clock.instant());
    agreement.setUpdatedByUserId(userId);
  }

  public MslaAgreement find(String reference, Long custodian) {
    return agreements
        .findScoped(SetupValidation.reference(reference), custodian)
        .orElseThrow(() -> SetupException.missing("MSLA not found in your scope"));
  }

  private MslaResponse response(MslaAgreement agreement, LenderMandate mandate) {
    var organizationIds =
        List.of(
            mandate.getLenderOrgId(),
            mandate.getCustodianOrgId(),
            agreement.getBorrowerOrgId());
    var parties = mandates.parties(organizationIds);
    return mapper.msla(agreement, mandate, parties, LocalDate.now(clock));
  }

  @Transactional(readOnly = true)
  public MslaResponse get(Authentication authentication, String reference) {
    var actor = access.actor(authentication, false);
    var agreement = find(reference, actor.getOrganizationId());
    var mandate = mandateRepository.findById(agreement.getMandateId()).orElseThrow();
    return response(agreement, mandate);
  }

  @Transactional(readOnly = true)
  public PageResponse<MslaResponse> list(
      Authentication authentication, int page, int size) {
    var actor = access.actor(authentication, false);
    Page<MslaAgreement> agreementsPage =
        agreements.findAll(
            (root, query, criteriaBuilder) -> {
              var mandateQuery = query.subquery(Long.class);
              var mandateRoot = mandateQuery.from(LenderMandate.class);
              mandateQuery
                  .select(mandateRoot.get("mandateId"))
                  .where(
                      criteriaBuilder.equal(
                          mandateRoot.get("custodianOrgId"), actor.getOrganizationId()));
              return root.get("mandateId").in(mandateQuery);
            },
            SetupValidation.page(page, size, "mslaReference"));

    var mandateIds =
        agreementsPage.getContent().stream()
            .map(MslaAgreement::getMandateId)
            .distinct()
            .toList();
    var mandatesById =
        mandateRepository.findAllById(mandateIds).stream()
            .collect(Collectors.toMap(LenderMandate::getMandateId, mandate -> mandate));

    var organizationIds = new ArrayList<Long>();
    for (MslaAgreement agreement : agreementsPage.getContent()) {
      var mandate = mandatesById.get(agreement.getMandateId());
      organizationIds.add(agreement.getBorrowerOrgId());
      organizationIds.add(mandate.getLenderOrgId());
      organizationIds.add(mandate.getCustodianOrgId());
    }
    var organizationsById = mandates.parties(organizationIds);
    return PageResponse.from(
        agreementsPage.map(
            agreement ->
                mapper.msla(
                    agreement,
                    mandatesById.get(agreement.getMandateId()),
                    organizationsById,
                    LocalDate.now(clock))));
  }
}
