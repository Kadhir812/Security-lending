package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.client.ValuationCalculationResponse;
import com.hexaware.custodian_service.dto.response.LoanRequestDecisionResponse;
import com.hexaware.custodian_service.entity.ContractStatusHistory;
import com.hexaware.custodian_service.entity.InventoryReservation;
import com.hexaware.custodian_service.entity.LenderInventory;
import com.hexaware.custodian_service.entity.LendingFee;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.LocateRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.AssetType;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.enums.HistoryActorType;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.LoanRequestStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.RuleStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.quickfix.LoanApprovalFixMessageBuilder;
import com.hexaware.custodian_service.repository.CollateralHaircutRepository;
import com.hexaware.custodian_service.repository.ContractStatusHistoryRepository;
import com.hexaware.custodian_service.repository.CounterpartyCreditLimitRepository;
import com.hexaware.custodian_service.repository.InventoryReservationRepository;
import com.hexaware.custodian_service.repository.LenderInventoryRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.LocateRequestRepository;
import com.hexaware.custodian_service.repository.LendingFeeRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.credit.CreditLimitRules;
import com.hexaware.custodian_service.service.fees.LendingFeePolicy;
import com.hexaware.custodian_service.service.inventory.InventoryQuantityRules;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanDecisionCommitter {
  private final SetupAccess access;
  private final LoanRequestRepository requests;
  private final LocateRequestRepository locates;
  private final LoanContractRepository contracts;
  private final InventoryReservationRepository reservations;
  private final ContractStatusHistoryRepository histories;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final LenderInventoryRepository inventories;
  private final CounterpartyCreditLimitRepository creditLimits;
  private final LendingFeeRepository fees;
  private final CollateralHaircutRepository haircuts;
  private final LendingFeePolicy feePolicy;
  private final OutgoingEventService outgoing;
  private final LoanApprovalFixMessageBuilder approvalFixMessages;
  private final Clock clock;

  @Value("${app.approval.max-price-age:PT24H}")
  private Duration maxPriceAge;

  @PostConstruct
  void validatePriceAgeConfiguration() {
    if (maxPriceAge == null || maxPriceAge.isNegative() || maxPriceAge.isZero()) {
      throw new IllegalStateException("app.approval.max-price-age must be positive");
    }
  }

  @Transactional
  public LoanRequestDecisionResponse approve(
      org.springframework.security.core.Authentication authentication,
      String requestReference,
      Long requestVersion,
      Long agreementVersion,
      Long mandateVersion,
      Long feeId,
      Long feeVersion,
      Long haircutId,
      Long haircutVersion,
      AssetType validatedSecurityType,
      String validatedSecurityCurrency,
      ValuationCalculationResponse valuation) {
    Organization custodian = access.actor(authentication, true);
    LoanRequest request = lockPendingRequest(requestReference, custodian.getOrganizationId());
    if (request.getStatus() == LoanRequestStatus.APPROVED) {
      return contracts
          .findByLoanRequestId(request.getRequestId())
          .map(contract -> response(request, contract))
          .orElseThrow(() -> SetupException.conflict("Approved request has no contract"));
    }
    if (request.getStatus() != LoanRequestStatus.PENDING) {
      throw SetupException.conflict("Only pending requests can be approved");
    }
    if (!Objects.equals(request.getVersion(), requestVersion)) {
      throw SetupException.conflict("Request changed during approval; retry with current state");
    }

    MslaAgreement agreement =
        agreements
            .findLocked(request.getMslaId())
            .orElseThrow(() -> SetupException.missing("Loan request MSLA not found"));
    var mandate =
        mandates
            .findById(agreement.getMandateId())
            .flatMap(
                row ->
                    mandates.findLocked(
                        row.getMandateReference(), custodian.getOrganizationId()))
            .orElseThrow(() -> SetupException.missing("Loan request not found in your scope"));
    validateAgreement(agreement, mandate, custodian, request);
    Organization lender = lockOrganization(mandate.getLenderOrgId());
    Organization borrower = lockOrganization(agreement.getBorrowerOrgId());
    requireActiveOrganization(lender, OrganizationType.LENDER);
    requireActiveOrganization(borrower, OrganizationType.BORROWER);

    if (!Objects.equals(validatedSecurityCurrency, agreement.getBaseCurrency())
        || agreement.getEligibleSecurities() == null
        || agreement.getEligibleSecurities().getAllowedTypes() == null
        || !agreement
            .getEligibleSecurities()
            .getAllowedTypes()
            .contains(validatedSecurityType)) {
      throw SetupException.conflict("Security is no longer active, eligible, or currency-matched");
    }

    LenderInventory inventory =
        inventories
            .findLocked(lender.getOrganizationId(), request.getSecurityIsin())
            .orElseThrow(() -> SetupException.conflict("No lender inventory exists for this security"));
    var credit =
        creditLimits
            .findLockedByMslaId(agreement.getMslaId())
            .orElseThrow(() -> SetupException.conflict("No MSLA credit limit exists"));
    var fee =
        fees
            .findByMslaIdOrderByEffectiveFromDescFeeIdDesc(agreement.getMslaId())
            .stream()
            .filter(rule -> rule.getFeeId().equals(feeId))
            .findFirst()
            .orElseThrow(() -> SetupException.conflict("Selected lending fee rule changed"));
    LendingFee currentFee =
        feePolicy.select(
            fees.findByMslaIdOrderByEffectiveFromDescFeeIdDesc(agreement.getMslaId()),
            request.getSecurityIsin(),
            LocalDate.now(clock));
    var effectiveHaircuts =
        haircuts.findByMslaIdOrderByEffectiveFromDescHaircutIdDesc(agreement.getMslaId()).stream()
            .filter(rule -> rule.getCollateralType() == CollateralType.CASH)
            .filter(rule -> agreement.getBaseCurrency().equals(rule.getCurrency()))
            .filter(rule -> !rule.getEffectiveFrom().isAfter(clock.instant()))
            .filter(rule -> rule.getEffectiveTo() == null || rule.getEffectiveTo().isAfter(clock.instant()))
            .toList();
    if (effectiveHaircuts.size() != 1
        || !effectiveHaircuts.get(0).getHaircutId().equals(haircutId)
        || !Objects.equals(effectiveHaircuts.get(0).getVersion(), haircutVersion)
        || effectiveHaircuts.get(0).getHaircutPercentage().signum() != 0
        || !Objects.equals(agreement.getVersion(), agreementVersion)
        || !Objects.equals(mandate.getVersion(), mandateVersion)
        || !Objects.equals(fee.getVersion(), feeVersion)
        || !Objects.equals(currentFee.getFeeId(), feeId)
        || credit.getStatus() != RuleStatus.ACTIVE) {
      throw SetupException.conflict("MSLA, mandate, fee, haircut, or credit configuration changed");
    }
    if (agreement.getCollateralType() != CollateralType.CASH
        || (agreement.getDefaultRules() != null && !agreement.getDefaultRules().isEmpty())) {
      throw SetupException.conflict("MSLA rules are not supported for approval");
    }
    validateCalculation(request, agreement, valuation);
    long availableQuantity =
        InventoryQuantityRules.available(
            inventory.getTotalQuantity(),
            inventory.getOnLoanQuantity(),
            inventory.getReservedQuantity());
    if (availableQuantity < request.getQuantity()) {
      throw SetupException.conflict("Insufficient available lender inventory");
    }
    BigDecimal availableCredit =
        CreditLimitRules.available(
            credit.getLimitAmount(), credit.getUsedAmount(), credit.getReservedAmount());
    if (availableCredit.compareTo(valuation.getCreditReservationAmount()) < 0) {
      throw SetupException.conflict("Insufficient available MSLA credit");
    }

    consumeLocate(request, agreement);
    inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());
    credit.setReservedAmount(credit.getReservedAmount().add(valuation.getCreditReservationAmount()));
    inventories.flush();
    creditLimits.flush();

    Instant now = clock.instant();
    String transactionReference = transactionReference(request.getRequestReference());
    Map<String, Object> valuationSnapshot = valuationSnapshot(valuation);
    Map<String, Object> collateralTerms =
        collateralTerms(agreement, valuation, effectiveHaircuts.get(0).getHaircutId());
    LoanContract contract =
        contracts.saveAndFlush(
            LoanContract.builder()
                .transactionReference(transactionReference)
                .loanRequestId(request.getRequestId())
                .quantity(request.getQuantity())
                .settlementCurrency(request.getSettlementCurrency())
                .agreedAnnualFeePct(fee.getAnnualFeePct())
                .agreedAnnualRebatePct(BigDecimal.ZERO.setScale(6))
                .dayCountBasis("ACT_365")
                .collateralCoveragePct(agreement.getCollateralCoveragePct())
                .collateralTerms(collateralTerms)
                .acceptedValuationSnapshot(valuationSnapshot)
                .latestValuationResultReference(valuation.getCalculationReference())
                .latestMarketPrice(valuation.getMarketPrice())
                .latestLoanValue(valuation.getLoanMarketValue())
                .latestRequiredCollateral(valuation.getRequiredCashCollateral())
                .latestAdjustedCollateral(valuation.getRequiredCashCollateral())
                .latestCoveragePct(valuation.getCoveragePercentage())
                .latestValuationAt(valuation.getPriceTimestamp())
                .reservedCreditAmount(valuation.getCreditReservationAmount())
                .usedCreditAmount(BigDecimal.ZERO.setScale(2))
                .settlementCalendarVersion(agreement.getSettlementCalendarCode())
                .tradeDate(LocalDate.now(clock))
                .settlementDate(request.getRequestedSettlementDate())
                .recallConditions(agreement.getRecallRule())
                .contractStatus(LoanContractStatus.APPROVED)
                .contractCreatedAt(now)
                .build());
    reservations.saveAndFlush(
        InventoryReservation.builder()
            .contractId(contract.getContractId())
            .inventoryId(inventory.getInventoryId())
            .reservedQuantity(request.getQuantity())
            .status(com.hexaware.custodian_service.enums.InventoryReservationStatus.ACTIVE)
            .createdAt(now)
            .expiresAt(ReservationReviewRules.reviewDeadline(request.getRequestedSettlementDate()))
            .build());
    histories.saveAndFlush(
        ContractStatusHistory.builder()
            .contractId(contract.getContractId())
            .newStatus(LoanContractStatus.APPROVED.name())
            .changedBy(access.user(authentication).getUserId())
            .actorType(HistoryActorType.USER)
            .reason("Loan request approved")
            .changedAt(now)
            .build());

    request.setStatus(LoanRequestStatus.APPROVED);
    request.setResponseReason(null);
    request.setRespondedAt(now);
    requests.flush();
    ensureFresh(valuation.getPriceTimestamp(), clock.instant());
    enqueueDecision(
        request,
        borrower.getOrganizationReference(),
        custodian,
        contract,
        valuation,
        agreement.getMslaReference());
    return response(request, contract);
  }

  private void consumeLocate(LoanRequest request, MslaAgreement agreement) {
    if (request.getLocateId() == null) return;
    LocateRequest locate =
        locates
            .findLockedByLocateId(request.getLocateId())
            .orElseThrow(() -> SetupException.conflict("Approved locate is unavailable"));
    if (!"APPROVED".equals(locate.getStatus())
        || (locate.getExpiryTime() != null && locate.getExpiryTime().isBefore(clock.instant()))
        || !Objects.equals(locate.getMslaId(), agreement.getMslaId())
        || !Objects.equals(locate.getSecurityIsin(), request.getSecurityIsin())
        || !Objects.equals(locate.getSubmittedByIssuer(), request.getSubmittedByIssuer())
        || !Objects.equals(locate.getSubmittedBySubject(), request.getSubmittedBySubject())
        || locate.getApprovedQuantity() == null
        || locate.getApprovedQuantity() < request.getQuantity()) {
      throw SetupException.conflict("Approved locate no longer matches this loan request");
    }
    locate.setStatus("USED");
  }

  @Transactional
  public LoanRequestDecisionResponse reject(
      org.springframework.security.core.Authentication authentication,
      String requestReference,
      String reason) {
    Organization custodian = access.actor(authentication, true);
    LoanRequest request = lockPendingRequest(requestReference, custodian.getOrganizationId());
    if (request.getStatus() == LoanRequestStatus.REJECTED) {
      if (Objects.equals(request.getResponseReason(), reason)) return response(request, null);
      throw SetupException.conflict("Request was already rejected with a different reason");
    }
    if (request.getStatus() != LoanRequestStatus.PENDING) {
      throw SetupException.conflict("A terminal request decision cannot be changed");
    }
    MslaAgreement agreement =
        agreements
            .findLocked(request.getMslaId())
            .orElseThrow(() -> SetupException.missing("Loan request MSLA not found"));
    var mandate =
        mandates
            .findById(agreement.getMandateId())
            .flatMap(
                row ->
                    mandates.findLocked(
                        row.getMandateReference(), custodian.getOrganizationId()))
            .orElseThrow(() -> SetupException.missing("Loan request not found in your scope"));
    var borrower =
        organizations
            .findById(agreement.getBorrowerOrgId())
            .orElseThrow(() -> SetupException.conflict("MSLA borrower is unavailable"));
    request.setStatus(LoanRequestStatus.REJECTED);
    request.setResponseReason(reason);
    request.setRespondedAt(clock.instant());
    requests.flush();
    enqueueDecision(
        request, borrower.getOrganizationReference(), custodian, null, null, agreement.getMslaReference());
    return response(request, null);
  }

  public LoanRequestDecisionResponse response(LoanRequest request, LoanContract contract) {
    return LoanRequestDecisionResponse.builder()
        .requestReference(request.getRequestReference())
        .status(request.getStatus())
        .reason(request.getResponseReason())
        .contractReference(contract == null ? null : contract.getTransactionReference())
        .loanMarketValue(contract == null ? null : contract.getLatestLoanValue())
        .requiredCashCollateral(
            contract == null ? null : contract.getLatestRequiredCollateral())
        .respondedAt(request.getRespondedAt())
        .build();
  }

  @Transactional
  public LoanRequestDecisionResponse republishApprovedDecision(
      LoanRequest request, Organization custodian, Organization borrower) {
    LoanContract contract =
        contracts
            .findByLoanRequestId(request.getRequestId())
            .orElseThrow(() -> SetupException.conflict("Approved request has no contract"));
    MslaAgreement agreement =
        agreements
            .findById(request.getMslaId())
            .orElseThrow(() -> SetupException.conflict("Approved request MSLA is unavailable"));
    enqueueDecision(
        request,
        borrower.getOrganizationReference(),
        custodian,
        contract,
        null,
        agreement.getMslaReference());
    return response(request, contract);
  }

  private LoanRequest lockPendingRequest(String reference, Long custodianId) {
    LoanRequest request =
        requests
            .findLockedByRequestReference(reference)
            .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement =
        agreements
            .findById(request.getMslaId())
            .orElseThrow(() -> SetupException.missing("Loan request not found"));
    var mandate =
        mandates
            .findById(agreement.getMandateId())
            .orElseThrow(() -> SetupException.missing("Loan request not found"));
    if (!mandate.getCustodianOrgId().equals(custodianId)) {
      throw SetupException.missing("Loan request not found in your Custodian scope");
    }
    return request;
  }

  private Organization lockOrganization(Long id) {
    Organization organization =
        organizations
            .findById(id)
            .orElseThrow(() -> SetupException.conflict("MSLA party is unavailable"));
    return organizations
        .findLocked(organization.getOrganizationReference())
        .orElseThrow(() -> SetupException.conflict("MSLA party is unavailable"));
  }

  private void requireActiveOrganization(Organization organization, OrganizationType type) {
    if (organization.getOrganizationType() != type
        || organization.getStatus() != OrganizationStatus.ACTIVE) {
      throw SetupException.conflict("An active " + type + " organization is required");
    }
  }

  private void validateAgreement(
      MslaAgreement agreement,
      com.hexaware.custodian_service.entity.LenderMandate mandate,
      Organization custodian,
      LoanRequest request) {
    LocalDate today = LocalDate.now(clock);
    if (custodian.getOrganizationType() != OrganizationType.CUSTODIAN
        || custodian.getStatus() != OrganizationStatus.ACTIVE
        || agreement.getAgreementStatus() != AgreementStatus.ACTIVE
        || mandate.getStatus() != MandateStatus.ACTIVE
        || !SetupValidation.covers(agreement.getEffectiveDate(), agreement.getExpiryDate(), today)
        || !SetupValidation.covers(
            agreement.getEffectiveDate(), agreement.getExpiryDate(), request.getRequestedSettlementDate())
        || !SetupValidation.covers(mandate.getEffectiveFrom(), mandate.getEffectiveTo(), today)
        || !SetupValidation.covers(
            mandate.getEffectiveFrom(), mandate.getEffectiveTo(), request.getRequestedSettlementDate())
        || !Objects.equals(request.getSettlementCurrency(), agreement.getBaseCurrency())) {
      throw SetupException.conflict("Request, mandate, or MSLA is not currently effective");
    }
    ReservationReviewRules.requireNotOverdue(
        request.getRequestedSettlementDate(), clock.instant());
  }

  private void validateCalculation(
      LoanRequest request, MslaAgreement agreement, ValuationCalculationResponse valuation) {
    LoanValuationRules.validate(
        request,
        agreement.getBaseCurrency(),
        agreement.getCollateralCoveragePct(),
        valuation,
        clock.instant(),
        maxPriceAge);
  }

  private void ensureFresh(Instant priceTimestamp, Instant now) {
    if (priceTimestamp == null
        || priceTimestamp.isAfter(now)
        || Duration.between(priceTimestamp, now).compareTo(maxPriceAge) > 0) {
      throw SetupException.conflict("Market price is missing, future-dated, or stale");
    }
  }

  private String transactionReference(String requestReference) {
    return "CTR-" + UUID.nameUUIDFromBytes(requestReference.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private Map<String, Object> valuationSnapshot(ValuationCalculationResponse result) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("calculationReference", result.getCalculationReference());
    snapshot.put("securityIsin", result.getSecurityIsin());
    snapshot.put("quantity", result.getQuantity());
    snapshot.put("marketPrice", result.getMarketPrice());
    snapshot.put("currency", result.getCurrency());
    snapshot.put("priceReference", result.getPriceReference());
    snapshot.put("priceSource", result.getPriceSource());
    snapshot.put("priceTimestamp", result.getPriceTimestamp());
    snapshot.put("calculatedAt", result.getCalculatedAt());
    snapshot.put("loanMarketValue", result.getLoanMarketValue());
    snapshot.put("requiredCashCollateral", result.getRequiredCashCollateral());
    snapshot.put("coveragePercentage", result.getCoveragePercentage());
    snapshot.put("cashHaircutPercentage", result.getCashHaircutPercentage());
    snapshot.put("creditReservationAmount", result.getCreditReservationAmount());
    return snapshot;
  }

  private Map<String, Object> collateralTerms(
      MslaAgreement agreement, ValuationCalculationResponse result, Long haircutId) {
    Map<String, Object> terms = new LinkedHashMap<>();
    terms.put("collateralType", "CASH");
    terms.put("currency", agreement.getBaseCurrency());
    terms.put("haircutId", haircutId);
    terms.put("haircutPercentage", BigDecimal.ZERO.setScale(4));
    terms.put("coveragePercentage", agreement.getCollateralCoveragePct());
    terms.put("requiredAmount", result.getRequiredCashCollateral());
    terms.put("loanMarketValue", result.getLoanMarketValue());
    terms.put("valuationReference", result.getCalculationReference());
    return terms;
  }

  private void enqueueDecision(
      LoanRequest request,
      String borrowerReference,
      Organization custodian,
      LoanContract contract,
      ValuationCalculationResponse valuation,
      String mslaReference) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("requestReference", request.getRequestReference());
    payload.put("borrowerOrganizationReference", borrowerReference);
    payload.put("custodianOrganizationReference", custodian.getOrganizationReference());
    payload.put("decisionStatus", request.getStatus().name());
    payload.put("decisionReason", request.getResponseReason());
    payload.put("decisionVersion", request.getVersion());
    payload.put("respondedAt", request.getRespondedAt());
    payload.put(
        "contract",
        contract == null ? null : decisionContract(request, contract, valuation));
    EventEnvelope event =
        EventEnvelope.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("LOAN_REQUEST_DECISION")
            .schemaVersion(1)
            .producerService("CUSTODIAN")
            .destinationService("BORROWER")
            .correlationId(request.getRequestReference())
            .aggregateReference(request.getRequestReference())
            .aggregateVersion(request.getVersion())
            .occurredAt(clock.instant())
            .senderOrganizationReference(custodian.getOrganizationReference())
            .receiverOrganizationReference(borrowerReference)
            .payload(payload)
            .build();
    if (request.getStatus() == LoanRequestStatus.APPROVED) {
      event.setPayload(
          Map.of(
              "fixMessage",
              approvalFixMessages.build(event, mslaReference, payload)));
    }
    outgoing.enqueue("LOAN_REQUEST", event);
  }

  private Map<String, Object> decisionContract(
      LoanRequest request, LoanContract contract, ValuationCalculationResponse valuation) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("requestReference", request.getRequestReference());
    result.put("securityIsin", request.getSecurityIsin());
    result.put("transactionReference", contract.getTransactionReference());
    result.put("quantity", contract.getQuantity());
    result.put("settlementCurrency", contract.getSettlementCurrency());
    result.put("agreedAnnualFeePct", contract.getAgreedAnnualFeePct());
    result.put("dayCountBasis", contract.getDayCountBasis());
    result.put("collateralCoveragePct", contract.getCollateralCoveragePct());
    result.put("collateralTerms", contract.getCollateralTerms());
    result.put("acceptedValuationSnapshot", contract.getAcceptedValuationSnapshot());
    Map<String, Object> snapshot = contract.getAcceptedValuationSnapshot();
    result.put(
        "loanMarketValue",
        valuation == null ? snapshot.get("loanMarketValue") : valuation.getLoanMarketValue());
    result.put(
        "requiredCashCollateral",
        valuation == null
            ? snapshot.get("requiredCashCollateral")
            : valuation.getRequiredCashCollateral());
    result.put("settlementCalendarVersion", contract.getSettlementCalendarVersion());
    result.put("tradeDate", contract.getTradeDate());
    result.put("settlementDate", contract.getSettlementDate());
    return result;
  }
}
