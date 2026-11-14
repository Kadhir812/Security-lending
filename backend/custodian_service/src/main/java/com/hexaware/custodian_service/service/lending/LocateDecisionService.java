package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.entity.LocateRequest;
import com.hexaware.custodian_service.entity.LenderInventory;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LenderInventoryRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LocateRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.inventory.InventoryQuantityRules;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocateDecisionService {
  private final SetupAccess access;
  private final LocateRequestRepository locates;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final LenderInventoryRepository inventories;
  private final SecurityReferenceClient securities;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<Map<String, Object>> pending(Authentication authentication) {
    var custodian = access.actor(authentication, false);
    return locates.findPendingByCustodian(custodian.getOrganizationId()).stream()
        .map(locate -> queueRow(locate, custodian.getOrganizationId()))
        .toList();
  }

  @Transactional
  public Map<String, Object> approve(
      Authentication authentication, String locateReference, long approvedQuantity) {
    var custodian = access.actor(authentication, true);
    LocateRequest locate = lockedLocate(locateReference);
    PartyContext parties = parties(locate, custodian.getOrganizationId());
    if ("APPROVED".equals(locate.getStatus())) {
      if (Objects.equals(locate.getApprovedQuantity(), approvedQuantity)) return result(locate);
      throw SetupException.conflict("Locate request already has a different decision");
    }
    if (!"PENDING".equals(locate.getStatus())) {
      throw SetupException.conflict("Only pending locate requests can be approved");
    }
    if (approvedQuantity <= 0 || approvedQuantity > locate.getQuantity()) {
      throw SetupException.invalid("Approved quantity must be positive and no greater than requested");
    }
    validateAgreement(parties);
    var security = securities.require(locate.getSecurityIsin());
    var eligible = parties.agreement().getEligibleSecurities();
    if (!"ACTIVE".equals(security.status())
        || !Objects.equals(security.isin(), locate.getSecurityIsin())
        || eligible == null
        || eligible.getAllowedTypes() == null
        || !eligible.getAllowedTypes().contains(security.securityType())
        || !Objects.equals(parties.agreement().getBaseCurrency(), security.currency())) {
      throw SetupException.conflict("Security is no longer eligible under this MSLA");
    }
    LenderInventory inventory =
        inventories
            .findLocked(
                parties.mandate().getLenderOrgId(), locate.getSecurityIsin())
            .orElseThrow(() -> SetupException.conflict("Lender has no inventory for this security"));
    long available =
        InventoryQuantityRules.available(
            inventory.getTotalQuantity(),
            inventory.getOnLoanQuantity(),
            inventory.getReservedQuantity());
    if (approvedQuantity > available) {
      throw SetupException.conflict(
          "Approved quantity exceeds current available inventory (" + available + ")");
    }

    Instant now = clock.instant();
    locate.setStatus("APPROVED");
    locate.setApprovedQuantity(approvedQuantity);
    locate.setRespondedAt(now);
    locate.setResponseReason(null);
    enqueueDecision(locate, parties.borrower().getOrganizationReference(), custodian, now);
    return result(locate);
  }

  @Transactional
  public Map<String, Object> reject(
      Authentication authentication, String locateReference, String reason) {
    String normalized = reason == null ? "" : reason.strip();
    if (normalized.isEmpty() || normalized.length() > 500) {
      throw SetupException.invalid("A rejection reason of 1 to 500 characters is required");
    }
    var custodian = access.actor(authentication, true);
    LocateRequest locate = lockedLocate(locateReference);
    PartyContext parties = parties(locate, custodian.getOrganizationId());
    if ("REJECTED".equals(locate.getStatus())) {
      if (Objects.equals(locate.getResponseReason(), normalized)) return result(locate);
      throw SetupException.conflict("Locate request already has a different decision");
    }
    if (!"PENDING".equals(locate.getStatus())) {
      throw SetupException.conflict("Only pending locate requests can be rejected");
    }
    Instant now = clock.instant();
    locate.setStatus("REJECTED");
    locate.setApprovedQuantity(null);
    locate.setRespondedAt(now);
    locate.setResponseReason(normalized);
    enqueueDecision(locate, parties.borrower().getOrganizationReference(), custodian, now);
    return result(locate);
  }

  private Map<String, Object> queueRow(LocateRequest locate, Long custodianId) {
    PartyContext parties = parties(locate, custodianId);
    long available =
        inventories
            .findByOrganizationIdAndSecurityIsin(
                parties.mandate().getLenderOrgId(), locate.getSecurityIsin())
            .map(
                row ->
                    InventoryQuantityRules.available(
                        row.getTotalQuantity(),
                        row.getOnLoanQuantity(),
                        row.getReservedQuantity()))
            .orElse(0L);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("locateReference", locate.getLocateReference());
    row.put("borrowerOrganizationReference", parties.borrower().getOrganizationReference());
    row.put("lenderOrganizationReference", parties.lender().getOrganizationReference());
    row.put("mslaReference", parties.agreement().getMslaReference());
    row.put("securityIsin", locate.getSecurityIsin());
    row.put("quantity", locate.getQuantity());
    row.put("availableQuantity", available);
    row.put("requestTime", locate.getRequestTime());
    row.put("status", locate.getStatus());
    return row;
  }

  private Map<String, Object> result(LocateRequest locate) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("locateReference", locate.getLocateReference());
    response.put("status", locate.getStatus());
    response.put("quantity", locate.getQuantity());
    response.put("approvedQuantity", locate.getApprovedQuantity());
    response.put("expiryTime", locate.getExpiryTime());
    response.put("respondedAt", locate.getRespondedAt());
    response.put("responseReason", locate.getResponseReason());
    return response;
  }

  private LocateRequest lockedLocate(String reference) {
    return locates
        .findLockedByLocateReference(reference)
        .orElseThrow(() -> SetupException.missing("Locate request not found"));
  }

  private PartyContext parties(LocateRequest locate, Long custodianId) {
    MslaAgreement agreement =
        agreements
            .findById(locate.getMslaId())
            .orElseThrow(() -> SetupException.missing("Locate MSLA not found"));
    LenderMandate mandate =
        mandates
            .findById(agreement.getMandateId())
            .orElseThrow(() -> SetupException.missing("Locate mandate not found"));
    if (!mandate.getCustodianOrgId().equals(custodianId)) {
      throw SetupException.missing("Locate request not found in your Custodian scope");
    }
    var borrower =
        organizations
            .findById(agreement.getBorrowerOrgId())
            .orElseThrow(() -> SetupException.conflict("Locate Borrower is unavailable"));
    var lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .orElseThrow(() -> SetupException.conflict("Locate lender is unavailable"));
    return new PartyContext(agreement, mandate, borrower, lender);
  }

  private void validateAgreement(PartyContext parties) {
    LocalDate today = LocalDate.now(clock);
    if (parties.agreement().getAgreementStatus() != AgreementStatus.ACTIVE
        || !covers(
            parties.agreement().getEffectiveDate(), parties.agreement().getExpiryDate(), today)
        || parties.mandate().getStatus() != MandateStatus.ACTIVE
        || !covers(
            parties.mandate().getEffectiveFrom(), parties.mandate().getEffectiveTo(), today)
        || parties.borrower().getStatus() != OrganizationStatus.ACTIVE
        || parties.borrower().getOrganizationType() != OrganizationType.BORROWER
        || parties.lender().getStatus() != OrganizationStatus.ACTIVE
        || parties.lender().getOrganizationType() != OrganizationType.LENDER) {
      throw SetupException.conflict("Locate parties or MSLA are no longer active");
    }
  }

  private boolean covers(LocalDate from, LocalDate to, LocalDate date) {
    return from != null && !date.isBefore(from) && (to == null || !date.isAfter(to));
  }

  private void enqueueDecision(
      LocateRequest locate,
      String borrowerReference,
      com.hexaware.custodian_service.entity.Organization custodian,
      Instant now) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("locateReference", locate.getLocateReference());
    payload.put("mslaReference", agreements.findById(locate.getMslaId()).orElseThrow().getMslaReference());
    payload.put("securityIsin", locate.getSecurityIsin());
    payload.put("quantity", locate.getQuantity());
    payload.put("approvedQuantity", locate.getApprovedQuantity());
    payload.put("decisionStatus", locate.getStatus());
    payload.put("responseReason", locate.getResponseReason());
    payload.put("respondedAt", now);
    payload.put("expiryTime", locate.getExpiryTime());
    payload.put("decisionVersion", 1L);
    payload.put("borrowerOrganizationReference", borrowerReference);
    payload.put("custodianOrganizationReference", custodian.getOrganizationReference());
    EventEnvelope event =
        EventEnvelope.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("LOCATE_REQUEST_DECISION")
            .schemaVersion(1)
            .producerService("CUSTODIAN")
            .destinationService("BORROWER")
            .correlationId(locate.getLocateReference())
            .aggregateReference(locate.getLocateReference())
            .aggregateVersion(1L)
            .occurredAt(now)
            .senderOrganizationReference(custodian.getOrganizationReference())
            .receiverOrganizationReference(borrowerReference)
            .payload(payload)
            .build();
    outgoing.enqueue("LOCATE_REQUEST", event);
  }

  private record PartyContext(
      MslaAgreement agreement,
      LenderMandate mandate,
      com.hexaware.custodian_service.entity.Organization borrower,
      com.hexaware.custodian_service.entity.Organization lender) {}
}
