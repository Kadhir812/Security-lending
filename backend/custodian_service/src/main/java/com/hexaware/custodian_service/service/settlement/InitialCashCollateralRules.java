package com.hexaware.custodian_service.service.settlement;

import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.enums.CollateralType;
import java.math.BigDecimal;
import java.util.List;

public final class InitialCashCollateralRules {
  private InitialCashCollateralRules() {}

  public static BigDecimal reservedCash(List<Collateral> collaterals) {
    return collaterals.stream()
        .filter(InitialCashCollateralRules::isReservedCash)
        .map(Collateral::getPostedCashAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public static BigDecimal postedCash(List<Collateral> collaterals) {
    return collaterals.stream()
        .filter(collateral -> isCash(collateral) && "POSTED".equals(collateral.getStatus()))
        .map(Collateral::getPostedCashAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public static boolean exceedsRequirement(
      BigDecimal committed, BigDecimal requested, BigDecimal required) {
    return committed.add(requested).compareTo(required) > 0;
  }

  public static boolean hasEarlierPendingCash(
      List<Collateral> collaterals, Collateral candidate) {
    Long candidateId = candidate.getCollateralId();
    return candidateId != null && collaterals.stream()
        .filter(InitialCashCollateralRules::isReservedCash)
        .filter(collateral -> "PENDING".equals(collateral.getStatus()))
        .map(Collateral::getCollateralId)
        .anyMatch(id -> id != null && id < candidateId);
  }

  private static boolean isReservedCash(Collateral collateral) {
    return isCash(collateral)
        && ("PENDING".equals(collateral.getStatus()) || "POSTED".equals(collateral.getStatus()));
  }

  private static boolean isCash(Collateral collateral) {
    return collateral.getCollateralType() == CollateralType.CASH;
  }
}
