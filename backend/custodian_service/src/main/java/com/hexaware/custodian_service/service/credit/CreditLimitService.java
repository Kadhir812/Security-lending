package com.hexaware.custodian_service.service.credit;

import com.hexaware.custodian_service.dto.request.CreditLimitCreateRequest;
import com.hexaware.custodian_service.dto.request.CreditLimitUpdateRequest;
import com.hexaware.custodian_service.dto.response.CreditLimitHistoryResponse;
import com.hexaware.custodian_service.dto.response.CreditLimitResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.entity.CounterpartyCreditLimit;
import com.hexaware.custodian_service.entity.CreditLimitHistory;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.enums.RuleStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.CounterpartyCreditLimitRepository;
import com.hexaware.custodian_service.repository.CreditLimitHistoryRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreditLimitService {

  private final CounterpartyCreditLimitRepository creditLimits;
  private final CreditLimitHistoryRepository history;
  private final MslaAgreementRepository agreements;
  private final LendingSetupAccess setupAccess;
  private final Clock clock;
  private final EntityManager entityManager;

  @Transactional
  public CreditLimitResponse create(
      Authentication authentication, String mslaReference, CreditLimitCreateRequest request) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, true);
    if (creditLimits.findLockedByMslaId(agreement.getMslaId()).isPresent()) {
      throw SetupException.conflict("A credit limit already exists for this MSLA");
    }
    String reason = SetupValidation.text(request.reason(), "reason", 500);
    var creditLimit =
        creditLimits.saveAndFlush(
            CounterpartyCreditLimit.builder()
                .mslaId(agreement.getMslaId())
                .limitAmount(request.limitAmount())
                .status(RuleStatus.ACTIVE)
                .build());
    history.saveAndFlush(
        CreditLimitHistory.builder()
            .creditLimitId(creditLimit.getCreditLimitId())
            .oldLimit(java.math.BigDecimal.ZERO)
            .newLimit(request.limitAmount())
            .reason(reason)
            .changedBy(setupAccess.actorUserId(authentication))
            .changedAt(clock.instant())
            .build());
    entityManager.refresh(creditLimit);
    return response(agreement.getMslaReference(), agreement.getBaseCurrency(), creditLimit);
  }

  @Transactional(readOnly = true)
  public CreditLimitResponse get(Authentication authentication, String mslaReference) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, false);
    var creditLimit =
        creditLimits
            .findByMslaId(agreement.getMslaId())
            .orElseThrow(() -> SetupException.missing("Credit limit not found"));
    return response(agreement.getMslaReference(), agreement.getBaseCurrency(), creditLimit);
  }

  @Transactional(readOnly = true)
  public PageResponse<CreditLimitResponse> list(
      Authentication authentication, int page, int size) {
    var actor = setupAccess.actor(authentication, false);
    var limits =
        creditLimits.findVisibleToCustodian(
            actor.getOrganizationId(),
            SetupValidation.page(page, size, "creditLimitId"));
    List<Long> mslaIds =
        limits.getContent().stream().map(CounterpartyCreditLimit::getMslaId).distinct().toList();
    Map<Long, MslaAgreement> agreementsById =
        agreements.findAllById(mslaIds).stream()
            .collect(Collectors.toMap(MslaAgreement::getMslaId, agreement -> agreement));
    return PageResponse.from(
        limits.map(
            creditLimit -> {
              var agreement = agreementsById.get(creditLimit.getMslaId());
              return response(
                  agreement.getMslaReference(), agreement.getBaseCurrency(), creditLimit);
            }));
  }

  @Transactional
  public CreditLimitResponse update(
      Authentication authentication,
      String mslaReference,
      CreditLimitUpdateRequest request) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, true);
    var creditLimit =
        creditLimits
            .findLockedByMslaId(agreement.getMslaId())
            .orElseThrow(() -> SetupException.missing("Credit limit not found"));
    SetupValidation.version(creditLimit.getVersion(), request.version());
    String reason = SetupValidation.text(request.reason(), "reason", 500);

    var oldLimit = creditLimit.getLimitAmount();
    creditLimit.setLimitAmount(request.limitAmount());
    creditLimit.setStatus(request.status());
    creditLimits.flush();
    if (oldLimit.compareTo(request.limitAmount()) != 0) {
      history.saveAndFlush(
          CreditLimitHistory.builder()
              .creditLimitId(creditLimit.getCreditLimitId())
              .oldLimit(oldLimit)
              .newLimit(request.limitAmount())
              .reason(reason)
              .changedBy(setupAccess.actorUserId(authentication))
              .changedAt(clock.instant())
              .build());
    }
    entityManager.refresh(creditLimit);
    return response(agreement.getMslaReference(), agreement.getBaseCurrency(), creditLimit);
  }

  @Transactional(readOnly = true)
  public List<CreditLimitHistoryResponse> history(
      Authentication authentication, String mslaReference) {
    var agreement = setupAccess.requireMsla(authentication, mslaReference, false);
    var creditLimit =
        creditLimits
            .findByMslaId(agreement.getMslaId())
            .orElseThrow(() -> SetupException.missing("Credit limit not found"));
    return history.findByCreditLimitIdOrderByChangedAtDesc(creditLimit.getCreditLimitId()).stream()
        .map(
            item ->
                new CreditLimitHistoryResponse(
                    item.getOldLimit(),
                    item.getNewLimit(),
                    item.getReason(),
                    item.getChangedBy(),
                    item.getChangedAt()))
        .toList();
  }

  private CreditLimitResponse response(
      String reference, String currency, CounterpartyCreditLimit creditLimit) {
    return new CreditLimitResponse(
        reference,
        currency,
        creditLimit.getLimitAmount(),
        creditLimit.getUsedAmount(),
        creditLimit.getReservedAmount(),
        CreditLimitRules.available(
            creditLimit.getLimitAmount(),
            creditLimit.getUsedAmount(),
            creditLimit.getReservedAmount()),
        creditLimit.getStatus(),
        creditLimit.getVersion());
  }
}
