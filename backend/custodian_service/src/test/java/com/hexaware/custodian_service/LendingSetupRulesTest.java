package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexaware.custodian_service.entity.LendingFee;
import com.hexaware.custodian_service.enums.RuleStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.service.credit.CreditLimitRules;
import com.hexaware.custodian_service.service.agreement.EffectiveDateRanges;
import com.hexaware.custodian_service.service.inventory.InventoryQuantityRules;
import com.hexaware.custodian_service.service.fees.LendingFeePolicy;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LendingSetupRulesTest {

  private final LendingFeePolicy feePolicy = new LendingFeePolicy();

  @Test
  void quantityAndCreditCalculationsPreserveEncumbrancesAndAllowNegativeCredit() {
    assertThat(InventoryQuantityRules.available(100, 25, 10)).isEqualTo(65);
    assertThat(CreditLimitRules.available(
            new BigDecimal("100.00"), new BigDecimal("80.00"), new BigDecimal("30.00")))
        .isEqualByComparingTo("-10.00");
    assertThatThrownBy(() -> InventoryQuantityRules.requireTotalCoversEncumbrances(34, 25, 10))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("reserved plus on-loan");
  }

  @Test
  void effectiveSecurityFeeOverridesAgreementDefaultAndFallsBackByDate() {
    var defaultFee = fee(null, "0.50", LocalDate.of(2026, 1, 1), null);
    var specificFee = fee("INE002A01018", "0.25", LocalDate.of(2026, 3, 1), null);

    assertThat(feePolicy.select(
            List.of(defaultFee, specificFee), "INE002A01018", LocalDate.of(2026, 4, 1)))
        .isSameAs(specificFee);
    assertThat(feePolicy.select(
            List.of(defaultFee, specificFee), "INE002A01018", LocalDate.of(2026, 2, 1)))
        .isSameAs(defaultFee);
  }

  @Test
  void activeFeePeriodsRejectInclusiveDateOverlap() {
    var first = fee("INE002A01018", "0.25", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    var touching = fee(
        "INE002A01018", "0.30", LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28));
    assertThat(feePolicy.overlaps(first, touching)).isTrue();
    assertThat(
            feePolicy.overlaps(
                first,
                fee("INE002A01018", "0.30", LocalDate.of(2026, 2, 1), null)))
        .isFalse();
  }

  @Test
  void haircutPeriodsAreHalfOpenWhileDatesMayTouchWithoutOverlap() {
    Instant boundary = Instant.parse("2026-04-01T00:00:00Z");
    assertThat(
            EffectiveDateRanges.overlapsHalfOpen(
                Instant.parse("2026-03-01T00:00:00Z"), boundary, boundary, null))
        .isFalse();
    assertThat(
            EffectiveDateRanges.overlapsInclusive(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 3, 31),
                null))
        .isTrue();
  }

  @Test
  void staleVersionFromConcurrentUpdateIsRejected() {
    assertThatThrownBy(() -> SetupValidation.version(4L, 3L))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("Version");
  }

  private LendingFee fee(String isin, String annualPct, LocalDate from, LocalDate to) {
    return LendingFee.builder()
        .securityIsin(isin)
        .annualFeePct(new BigDecimal(annualPct))
        .effectiveFrom(from)
        .effectiveTo(to)
        .status(RuleStatus.ACTIVE)
        .build();
  }
}
