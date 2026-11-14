package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.dto.request.LendingFeeRequest;
import com.hexaware.custodian_service.dto.response.LendingFeeResponse;
import com.hexaware.custodian_service.entity.LendingFee;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LendingFeeRepository;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LendingFeeService {

  private final LendingFeeRepository fees;
  private final LendingSetupAccess setupAccess;
  private final LendingFeePolicy policy;

  @Transactional
  public LendingFeeResponse create(
      Authentication authentication, String mslaReference, LendingFeeRequest request) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, true);
    SetupValidation.newVersion(request.version());
    var candidate = candidate(agreement, request);
    rejectOverlaps(
        candidate, fees.findByMslaIdOrderByEffectiveFromDescFeeIdDesc(agreement.getMslaId()));
    return response(agreement.getMslaReference(), fees.saveAndFlush(candidate));
  }

  @Transactional(readOnly = true)
  public List<LendingFeeResponse> list(Authentication authentication, String mslaReference) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, false);
    return fees.findByMslaIdOrderByEffectiveFromDescFeeIdDesc(agreement.getMslaId()).stream()
        .map(fee -> response(agreement.getMslaReference(), fee))
        .toList();
  }

  @Transactional(readOnly = true)
  public LendingFeeResponse get(
      Authentication authentication, String mslaReference, Long feeId) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, false);
    return fees
        .findByFeeIdAndMslaId(feeId, agreement.getMslaId())
        .map(fee -> response(agreement.getMslaReference(), fee))
        .orElseThrow(() -> SetupException.missing("Lending fee not found"));
  }

  @Transactional
  public LendingFeeResponse update(
      Authentication authentication,
      String mslaReference,
      Long feeId,
      LendingFeeRequest request) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, true);
    var fee =
        fees.findLocked(feeId, agreement.getMslaId())
            .orElseThrow(() -> SetupException.missing("Lending fee not found"));
    SetupValidation.version(fee.getVersion(), request.version());
    var candidate = candidate(agreement, request);
    candidate.setFeeId(fee.getFeeId());
    rejectOverlaps(
        candidate,
        fees.findByMslaIdOrderByEffectiveFromDescFeeIdDesc(agreement.getMslaId()).stream()
            .filter(existing -> !existing.getFeeId().equals(feeId))
            .toList());
    fee.setSecurityIsin(candidate.getSecurityIsin());
    fee.setAnnualFeePct(candidate.getAnnualFeePct());
    fee.setEffectiveFrom(candidate.getEffectiveFrom());
    fee.setEffectiveTo(candidate.getEffectiveTo());
    fee.setStatus(candidate.getStatus());
    fees.flush();
    return response(agreement.getMslaReference(), fee);
  }

  @Transactional(readOnly = true)
  public LendingFeeResponse selectEffective(
      Authentication authentication,
      String mslaReference,
      String securityIsin,
      LocalDate businessDate) {
    if (businessDate == null) {
      throw SetupException.invalid("businessDate is required");
    }
    var agreement = setupAccess.requireMsla(authentication, mslaReference, false);
    var security = setupAccess.requireSecurity(securityIsin);
    setupAccess.requireEligible(agreement, security);
    var selected =
        policy.select(
            fees.findByMslaIdOrderByEffectiveFromDescFeeIdDesc(agreement.getMslaId()),
            security.isin(),
            businessDate);
    return response(agreement.getMslaReference(), selected);
  }

  private LendingFee candidate(
      com.hexaware.custodian_service.entity.MslaAgreement agreement,
      LendingFeeRequest request) {
    SetupValidation.dates(request.effectiveFrom(), request.effectiveTo());
    String securityIsin = null;
    if (request.securityIsin() != null) {
      var security = setupAccess.requireSecurity(request.securityIsin());
      setupAccess.requireEligible(agreement, security);
      securityIsin = security.isin();
    }
    return LendingFee.builder()
        .mslaId(agreement.getMslaId())
        .securityIsin(securityIsin)
        .annualFeePct(request.annualFeePct())
        .effectiveFrom(request.effectiveFrom())
        .effectiveTo(request.effectiveTo())
        .status(request.status())
        .build();
  }

  private void rejectOverlaps(LendingFee candidate, List<LendingFee> existingRules) {
    boolean overlap =
        existingRules.stream().anyMatch(existing -> policy.overlaps(existing, candidate));
    if (overlap) {
      throw SetupException.conflict(
          "An active lending fee already overlaps this security scope and date range");
    }
  }

  private LendingFeeResponse response(String mslaReference, LendingFee fee) {
    return new LendingFeeResponse(
        fee.getFeeId(),
        mslaReference,
        fee.getSecurityIsin(),
        fee.getAnnualFeePct(),
        fee.getEffectiveFrom(),
        fee.getEffectiveTo(),
        fee.getStatus(),
        fee.getVersion());
  }
}
