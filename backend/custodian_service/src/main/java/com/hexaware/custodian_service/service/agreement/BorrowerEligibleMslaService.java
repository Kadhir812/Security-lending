package com.hexaware.custodian_service.service.agreement;

import com.hexaware.custodian_service.dto.response.BorrowerEligibleMslaResponse;
import com.hexaware.custodian_service.entity.LendingFee;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.enums.RuleStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LendingFeeRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.BorrowerAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BorrowerEligibleMslaService {
  private final BorrowerAccess access;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final LendingFeeRepository fees;
  private final OrganizationRepository organizations;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<BorrowerEligibleMslaResponse> list(Authentication authentication) {
    var borrower = access.borrower(authentication);
    return agreements
        .findEligibleForBorrower(borrower.getOrganizationId(), LocalDate.now(clock))
        .stream()
        .map(this::response)
        .toList();
  }

  @Transactional(readOnly = true)
  public BorrowerEligibleMslaResponse get(Authentication authentication, String reference) {
    return list(authentication).stream()
        .filter(msla -> msla.mslaReference().equals(reference))
        .findFirst()
        .orElseThrow(() -> SetupException.missing("Eligible MSLA not found"));
  }

  private BorrowerEligibleMslaResponse response(MslaAgreement agreement) {
    var mandate =
        mandates
            .findById(agreement.getMandateId())
            .orElseThrow(() -> SetupException.conflict("MSLA mandate is unavailable"));
    var lender =
        organizations
            .findById(mandate.getLenderOrgId())
            .orElseThrow(() -> SetupException.conflict("MSLA lender is unavailable"));
    var borrower =
        organizations
            .findById(agreement.getBorrowerOrgId())
            .orElseThrow(() -> SetupException.conflict("MSLA borrower is unavailable"));
    var custodian =
        organizations
            .findById(mandate.getCustodianOrgId())
            .orElseThrow(() -> SetupException.conflict("MSLA custodian is unavailable"));
    var eligible = agreement.getEligibleSecurities();
    var types =
        eligible == null || eligible.getAllowedTypes() == null
            ? List.<String>of()
            : eligible.getAllowedTypes().stream().map(Enum::name).toList();

    return new BorrowerEligibleMslaResponse(
        agreement.getMslaReference(),
        mandate.getMandateReference(),
        lender.getOrganizationReference(),
        borrower.getOrganizationReference(),
        custodian.getOrganizationReference(),
        agreement.getEffectiveDate(),
        agreement.getExpiryDate(),
        agreement.getAgreementStatus(),
        mandate.getEffectiveFrom(),
        mandate.getEffectiveTo(),
        mandate.getStatus(),
        agreement.getBaseCurrency(),
        agreementDefaultFee(agreement),
        agreement.getAnnualRebatePct(),
        types,
        eligible);
  }

  private BigDecimal agreementDefaultFee(MslaAgreement agreement) {
    LocalDate today = LocalDate.now(clock);
    return fees.findByMslaIdOrderByEffectiveFromDescFeeIdDesc(agreement.getMslaId()).stream()
        .filter(fee -> fee.getSecurityIsin() == null)
        .filter(fee -> fee.getStatus() == RuleStatus.ACTIVE)
        .filter(fee -> !today.isBefore(fee.getEffectiveFrom()))
        .filter(fee -> fee.getEffectiveTo() == null || !today.isAfter(fee.getEffectiveTo()))
        .map(LendingFee::getAnnualFeePct)
        .findFirst()
        .orElse(null);
  }
}
