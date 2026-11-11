package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.dto.request.LocateDecisionRequest;
import com.hexaware.custodian_service.dto.request.LocateSubmissionRequest;
import com.hexaware.custodian_service.dto.response.LocateResponse;
import com.hexaware.custodian_service.entity.LocateRequest;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LenderInventoryRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LocateRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.BorrowerAccess;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.agreement.BorrowerEligibleMslaService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocateService {
  private final BorrowerAccess borrowerAccess;
  private final SetupAccess setupAccess;
  private final BorrowerEligibleMslaService eligibleMslas;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final LenderInventoryRepository inventory;
  private final LocateRequestRepository locates;
  private final OrganizationRepository organizations;
  private final Clock clock;

  @Transactional
  public LocateResponse submit(Authentication authentication, LocateSubmissionRequest request) {
    var borrower = borrowerAccess.borrower(authentication);
    var mslaView = eligibleMslas.get(authentication, request.mslaReference());
    var agreement = agreements.findByMslaReference(request.mslaReference())
        .orElseThrow(() -> SetupException.missing("Eligible MSLA not found"));
    Long lenderId = organizations.findByOrganizationReference(mslaView.lenderOrganizationReference())
        .orElseThrow(() -> SetupException.conflict("MSLA lender is unavailable"))
        .getOrganizationId();
    var holding = inventory.findByOrganizationIdAndSecurityIsin(lenderId, request.securityIsin())
        .orElseThrow(() -> SetupException.conflict("Lender has no inventory for this security"));
    if (holding.getAvailableQuantity() == null || holding.getAvailableQuantity() < request.quantity())
      throw SetupException.conflict("Requested locate quantity exceeds available inventory");
    if (!(authentication instanceof JwtAuthenticationToken jwt))
      throw SetupException.invalid("Authenticated borrower JWT required");
    var now = clock.instant();
    var entity = locates.saveAndFlush(LocateRequest.builder()
        .locateReference("LOC-" + UUID.randomUUID())
        .mslaId(agreement.getMslaId())
        .securityIsin(request.securityIsin())
        .quantity(request.quantity())
        .submittedByIssuer(jwt.getToken().getIssuer().toString())
        .submittedBySubject(jwt.getToken().getSubject())
        .requestTime(now)
        .expiryTime(now.plus(Duration.ofDays(1)))
        .status("PENDING")
        .build());
    return response(entity, request.mslaReference(), borrower.getOrganizationReference());
  }

  @Transactional(readOnly = true)
  public List<LocateResponse> list(Authentication authentication) {
    var actor = setupAccess.actor(authentication, false);
    return locates.findVisibleToCustodian(actor.getOrganizationId()).stream()
        .map(this::response).toList();
  }

  @Transactional(readOnly = true)
  public LocateResponse get(Authentication authentication, String reference) {
    var actor = setupAccess.actor(authentication, false);
    return locates.findVisibleByReference(actor.getOrganizationId(), reference)
        .map(this::response).orElseThrow(() -> SetupException.missing("Locate not found"));
  }

  @Transactional(readOnly = true)
  public LocateResponse getForBorrower(Authentication authentication, String reference) {
    var borrower = borrowerAccess.borrower(authentication);
    var entity = locates.findByLocateReference(reference)
        .orElseThrow(() -> SetupException.missing("Locate not found"));
    var agreement = agreements.findById(entity.getMslaId()).orElseThrow();
    if (!agreement.getBorrowerOrgId().equals(borrower.getOrganizationId()))
      throw SetupException.missing("Locate not found");
    return response(entity);
  }

  @Transactional
  public LocateResponse approve(
      Authentication authentication, String reference, LocateDecisionRequest decision) {
    var actor = setupAccess.actor(authentication, true);
    var locate = locates.findVisibleByReference(actor.getOrganizationId(), reference)
        .orElseThrow(() -> SetupException.missing("Locate not found"));
    if (!"PENDING".equals(locate.getStatus()))
      throw SetupException.conflict("Only pending locates can be approved");
    if (!locate.getExpiryTime().isAfter(clock.instant()))
      throw SetupException.conflict("Locate request has expired");
    if (decision == null || decision.approvedQuantity() == null
        || decision.approvedQuantity() > locate.getQuantity())
      throw SetupException.invalid("Approved quantity must be positive and no greater than requested");
    var agreement = agreements.findById(locate.getMslaId()).orElseThrow();
    var mandate = mandates.findById(agreement.getMandateId()).orElseThrow();
    var holding = inventory.findLocked(mandate.getLenderOrgId(), locate.getSecurityIsin())
        .orElseThrow(() -> SetupException.conflict("Lender inventory is unavailable"));
    if (holding.getAvailableQuantity() == null
        || holding.getAvailableQuantity() < decision.approvedQuantity())
      throw SetupException.conflict("Approved quantity exceeds available inventory");
    locate.setApprovedQuantity(decision.approvedQuantity());
    locate.setStatus("APPROVED");
    locate.setRespondedAt(clock.instant());
    locate.setResponseReason(decision.reason());
    locates.save(locate);
    return response(locate);
  }

  @Transactional
  public LocateResponse reject(Authentication authentication, String reference, String reason) {
    var actor = setupAccess.actor(authentication, true);
    var locate = locates.findVisibleByReference(actor.getOrganizationId(), reference)
        .orElseThrow(() -> SetupException.missing("Locate not found"));
    if (!"PENDING".equals(locate.getStatus()))
      throw SetupException.conflict("Only pending locates can be rejected");
    locate.setStatus("REJECTED");
    locate.setRespondedAt(clock.instant());
    locate.setResponseReason(reason);
    locates.save(locate);
    return response(locate);
  }

  private LocateResponse response(LocateRequest locate) {
    var agreement = agreements.findById(locate.getMslaId()).orElseThrow();
    var borrower = organizations.findById(agreement.getBorrowerOrgId()).orElseThrow();
    return response(locate, agreement.getMslaReference(), borrower.getOrganizationReference());
  }

  private LocateResponse response(LocateRequest locate, String msla, String borrower) {
    return new LocateResponse(locate.getLocateReference(), msla, borrower,
        locate.getSecurityIsin(), locate.getQuantity(), locate.getApprovedQuantity(),
        locate.getStatus(), locate.getRequestTime(), locate.getExpiryTime(),
        locate.getRespondedAt(), locate.getResponseReason());
  }
}
