package com.hexaware.custodian_service.service.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.enums.CollateralType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InitialCashCollateralRulesTest {
  @Test
  void reservesPendingAndPostedCashButNotFailedCollateral() {
    List<Collateral> collaterals = List.of(
        collateral(1L, "PENDING", "40.00"),
        collateral(2L, "POSTED", "50.00"),
        collateral(3L, "FAILED", "100.00"),
        Collateral.builder()
            .collateralId(4L)
            .collateralType(CollateralType.ETF)
            .status("PENDING")
            .postedCashAmount(BigDecimal.ZERO)
            .build());

    assertThat(InitialCashCollateralRules.reservedCash(collaterals))
        .isEqualByComparingTo("90.00");
    assertThat(InitialCashCollateralRules.exceedsRequirement(
        InitialCashCollateralRules.reservedCash(collaterals),
        new BigDecimal("10.00"),
        new BigDecimal("100.00")))
        .isFalse();
    assertThat(InitialCashCollateralRules.exceedsRequirement(
        InitialCashCollateralRules.reservedCash(collaterals),
        new BigDecimal("10.01"),
        new BigDecimal("100.00")))
        .isTrue();
  }

  @Test
  void onlyTheOldestPendingCashInstructionCanBeConfirmedFirst() {
    Collateral first = collateral(1L, "PENDING", "100.00");
    Collateral second = collateral(2L, "PENDING", "100.00");

    assertThat(InitialCashCollateralRules.hasEarlierPendingCash(List.of(first, second), first))
        .isFalse();
    assertThat(InitialCashCollateralRules.hasEarlierPendingCash(List.of(first, second), second))
        .isTrue();

    first.setStatus("POSTED");
    assertThat(InitialCashCollateralRules.hasEarlierPendingCash(List.of(first, second), second))
        .isFalse();
  }

  private Collateral collateral(Long id, String status, String amount) {
    return Collateral.builder()
        .collateralId(id)
        .collateralType(CollateralType.CASH)
        .postedCashAmount(new BigDecimal(amount))
        .status(status)
        .build();
  }
}
