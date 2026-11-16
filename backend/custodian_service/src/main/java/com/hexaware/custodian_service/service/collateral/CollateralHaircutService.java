package com.hexaware.custodian_service.service.collateral;

import com.hexaware.custodian_service.dto.request.CollateralHaircutRequest;
import com.hexaware.custodian_service.dto.response.CollateralHaircutResponse;
import com.hexaware.custodian_service.entity.CollateralHaircut;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.CollateralHaircutRepository;
import com.hexaware.custodian_service.service.agreement.EffectiveDateRanges;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollateralHaircutService {

  private final CollateralHaircutRepository haircuts;
  private final LendingSetupAccess setupAccess;

  @Transactional
  public CollateralHaircutResponse create(
      Authentication authentication,
      String mslaReference,
      CollateralHaircutRequest request) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, true);
    SetupValidation.newVersion(request.version());
    var candidate = candidate(agreement.getMslaId(), agreement.getBaseCurrency(), request);
    rejectOverlap(
        candidate,
        haircuts.findByMslaIdOrderByEffectiveFromDescHaircutIdDesc(agreement.getMslaId()));
    return response(agreement.getMslaReference(), haircuts.saveAndFlush(candidate));
  }

  @Transactional(readOnly = true)
  public List<CollateralHaircutResponse> list(
      Authentication authentication, String mslaReference) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, false);
    return haircuts
        .findByMslaIdOrderByEffectiveFromDescHaircutIdDesc(agreement.getMslaId())
        .stream()
        .map(haircut -> response(agreement.getMslaReference(), haircut))
        .toList();
  }

  @Transactional(readOnly = true)
  public CollateralHaircutResponse get(
      Authentication authentication, String mslaReference, Long haircutId) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, false);
    return haircuts
        .findById(haircutId)
        .filter(haircut -> haircut.getMslaId().equals(agreement.getMslaId()))
        .map(haircut -> response(agreement.getMslaReference(), haircut))
        .orElseThrow(() -> SetupException.missing("Collateral haircut not found"));
  }

  @Transactional
  public CollateralHaircutResponse update(
      Authentication authentication,
      String mslaReference,
      Long haircutId,
      CollateralHaircutRequest request) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, true);
    var haircut =
        haircuts.findLocked(haircutId, agreement.getMslaId())
            .orElseThrow(() -> SetupException.missing("Collateral haircut not found"));
    SetupValidation.version(haircut.getVersion(), request.version());
    var candidate = candidate(agreement.getMslaId(), agreement.getBaseCurrency(), request);
    rejectOverlap(
        candidate,
        haircuts.findByMslaIdOrderByEffectiveFromDescHaircutIdDesc(agreement.getMslaId()).stream()
            .filter(existing -> !existing.getHaircutId().equals(haircutId))
            .toList());
    haircut.setCollateralType(candidate.getCollateralType());
    haircut.setCurrency(candidate.getCurrency());
    haircut.setHaircutPercentage(candidate.getHaircutPercentage());
    haircut.setEffectiveFrom(candidate.getEffectiveFrom());
    haircut.setEffectiveTo(candidate.getEffectiveTo());
    haircuts.flush();
    return response(agreement.getMslaReference(), haircut);
  }

  private CollateralHaircut candidate(
      Long mslaId, String agreementCurrency, CollateralHaircutRequest request) {
    if (request.collateralType() != CollateralType.CASH) {
      throw SetupException.invalid("Only CASH collateral haircuts are supported");
    }
    String currency = SetupValidation.currency(request.currency());
    if (!currency.equals(agreementCurrency)) {
      throw SetupException.invalid("CASH haircut currency must match the MSLA base currency");
    }
    if (request.haircutPercentage().compareTo(BigDecimal.ZERO) != 0) {
      throw SetupException.invalid("CASH haircut must be exactly zero percent");
    }
    if (request.effectiveFrom() == null
        || (request.effectiveTo() != null
            && !request.effectiveTo().isAfter(request.effectiveFrom()))) {
      throw SetupException.invalid("Invalid effective haircut date range");
    }
    return CollateralHaircut.builder()
        .mslaId(mslaId)
        .collateralType(CollateralType.CASH)
        .currency(currency)
        .haircutPercentage(BigDecimal.ZERO)
        .effectiveFrom(request.effectiveFrom())
        .effectiveTo(request.effectiveTo())
        .build();
  }

  private void rejectOverlap(
      CollateralHaircut candidate, List<CollateralHaircut> existingRules) {
    boolean overlap =
        existingRules.stream().anyMatch(existing -> overlaps(existing, candidate));
    if (overlap) {
      throw SetupException.conflict("An effective collateral haircut already overlaps this date range");
    }
  }

  private boolean overlaps(CollateralHaircut existing, CollateralHaircut candidate) {
    if (!existing.getCurrency().equals(candidate.getCurrency())
        || existing.getCollateralType() != candidate.getCollateralType()) {
      return false;
    }
    return EffectiveDateRanges.overlapsHalfOpen(
        existing.getEffectiveFrom(),
        existing.getEffectiveTo(),
        candidate.getEffectiveFrom(),
        candidate.getEffectiveTo());
  }

  private CollateralHaircutResponse response(String mslaReference, CollateralHaircut haircut) {
    return new CollateralHaircutResponse(
        haircut.getHaircutId(),
        mslaReference,
        haircut.getCollateralType(),
        haircut.getCurrency(),
        haircut.getHaircutPercentage(),
        haircut.getEffectiveFrom(),
        haircut.getEffectiveTo(),
        haircut.getVersion());
  }
}
