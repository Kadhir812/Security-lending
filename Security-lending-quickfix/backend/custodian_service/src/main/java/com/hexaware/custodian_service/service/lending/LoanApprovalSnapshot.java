package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.client.ValuationCalculationResponse;
import com.hexaware.custodian_service.entity.LendingFee;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.LenderMandate;
import java.util.Objects;

record LoanApprovalSnapshot(
    Long requestVersion,
    Long agreementVersion,
    Long mandateVersion,
    LendingFee fee,
    Long haircutId,
    Long haircutVersion,
    ValuationCalculationResponse valuation) {
  boolean sameRules(
      MslaAgreement agreement, LenderMandate mandate, LendingFee currentFee, Long currentHaircutId,
      Long currentHaircutVersion) {
    return Objects.equals(agreementVersion, agreement.getVersion())
        && Objects.equals(mandateVersion, mandate.getVersion())
        && Objects.equals(fee.getFeeId(), currentFee.getFeeId())
        && Objects.equals(fee.getVersion(), currentFee.getVersion())
        && Objects.equals(haircutId, currentHaircutId)
        && Objects.equals(haircutVersion, currentHaircutVersion);
  }
}
