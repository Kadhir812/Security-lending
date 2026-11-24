package com.hexaware.custodian_service.service.agreement;

import com.hexaware.custodian_service.dto.request.MslaSetupRequest;
import com.hexaware.custodian_service.dto.request.CollateralHaircutRequest;
import com.hexaware.custodian_service.dto.request.LendingFeeRequest;
import com.hexaware.custodian_service.dto.response.MslaSetupResponse;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.RuleStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.dto.response.MandateResponse;
import com.hexaware.custodian_service.service.collateral.CollateralHaircutService;
import com.hexaware.custodian_service.service.credit.CreditLimitService;
import com.hexaware.custodian_service.service.fees.LendingFeeService;
import com.hexaware.custodian_service.service.organization.MandateService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MslaSetupService {

  private final MandateService mandates;
  private final MslaService agreements;
  private final CreditLimitService creditLimits;
  private final CollateralHaircutService haircuts;
  private final LendingFeeService fees;
  private final Clock clock;

  @Transactional
  public MslaSetupResponse create(Authentication authentication, MslaSetupRequest request) {
    validateBundle(request);

    MandateResponse mandate =
        request.mandate() != null
            ? mandates.create(authentication, request.mandate())
            : mandates.get(authentication, request.existingMandateReference());
    if (mandate.getStatus() != MandateStatus.ACTIVE || !mandate.isCurrentlyEffective()) {
      throw SetupException.conflict("MSLA setup requires a currently active lender mandate");
    }
    var agreement = agreements.createActive(authentication, request.agreement());
    var creditLimit =
        creditLimits.create(
            authentication, agreement.getMslaReference(), request.creditLimit());
    var effectiveFrom =
        request.agreement().getEffectiveDate().atStartOfDay().toInstant(ZoneOffset.UTC);
    var effectiveTo =
        request.agreement().getExpiryDate() == null
            ? null
            : request.agreement()
                .getExpiryDate()
                .plusDays(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    var haircut =
        haircuts.create(
            authentication,
            agreement.getMslaReference(),
            new CollateralHaircutRequest(
                CollateralType.CASH,
                request.agreement().getBaseCurrency(),
                BigDecimal.ZERO,
                effectiveFrom,
                effectiveTo,
                null));
    var fee =
        fees.create(
            authentication,
            agreement.getMslaReference(),
            new LendingFeeRequest(
                null,
                request.annualFeePct(),
                request.agreement().getEffectiveDate(),
                request.agreement().getExpiryDate(),
                RuleStatus.ACTIVE,
                null));

    return new MslaSetupResponse(mandate, agreement, creditLimit, haircut, fee);
  }

  private void validateBundle(MslaSetupRequest request) {
    boolean creatingMandate = request.mandate() != null;
    boolean reusingMandate = request.existingMandateReference() != null;
    if (creatingMandate == reusingMandate) {
      throw SetupException.invalid("Choose an existing mandate or provide a new mandate, not both");
    }
    if (request.agreement().getAgreementStatus() != AgreementStatus.ACTIVE) {
      throw SetupException.invalid("A new MSLA setup must be ACTIVE immediately");
    }
    if (creatingMandate) {
      if (request.mandate().getStatus() != MandateStatus.ACTIVE) {
        throw SetupException.invalid("A new MSLA setup requires an ACTIVE mandate");
      }
      if (!request.mandate().getMandateReference().equals(request.agreement().getMandateReference())) {
        throw SetupException.invalid("The agreement must reference the new mandate");
      }
      if (request.mandate().getEffectiveFrom().isAfter(request.agreement().getEffectiveDate())
          || (request.mandate().getEffectiveTo() != null
              && (request.agreement().getExpiryDate() == null
                  || request.mandate()
                      .getEffectiveTo()
                      .isBefore(request.agreement().getExpiryDate())))) {
        throw SetupException.invalid("The mandate dates must cover the full MSLA period");
      }
    } else {
      if (!request.existingMandateReference().equals(request.agreement().getMandateReference())) {
        throw SetupException.invalid("The agreement must reference the selected active mandate");
      }
    }
    if (request.agreement().getEffectiveDate().isAfter(LocalDate.now(clock))) {
      throw SetupException.invalid("An immediately active MSLA must be effective today or earlier");
    }
  }
}
