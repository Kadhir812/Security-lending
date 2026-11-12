package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.entity.LendingFee;
import com.hexaware.custodian_service.enums.RuleStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.service.agreement.EffectiveDateRanges;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class LendingFeePolicy {

  public boolean overlaps(LendingFee existing, LendingFee candidate) {
    if (existing.getStatus() != RuleStatus.ACTIVE
        || candidate.getStatus() != RuleStatus.ACTIVE
        || !Objects.equals(existing.getSecurityIsin(), candidate.getSecurityIsin())) {
      return false;
    }
    return EffectiveDateRanges.overlapsInclusive(
        existing.getEffectiveFrom(),
        existing.getEffectiveTo(),
        candidate.getEffectiveFrom(),
        candidate.getEffectiveTo());
  }

  public LendingFee select(List<LendingFee> rules, String securityIsin, LocalDate businessDate) {
    var applicable =
        rules.stream()
            .filter(rule -> rule.getStatus() == RuleStatus.ACTIVE)
            .filter(
                rule ->
                    !businessDate.isBefore(rule.getEffectiveFrom())
                        && (rule.getEffectiveTo() == null
                            || !businessDate.isAfter(rule.getEffectiveTo())))
            .toList();
    var specific =
        applicable.stream()
            .filter(rule -> Objects.equals(rule.getSecurityIsin(), securityIsin))
            .toList();
    if (specific.size() > 1) {
      throw SetupException.conflict("Multiple effective security-specific lending fees exist");
    }
    if (!specific.isEmpty()) {
      return specific.getFirst();
    }
    var defaults = applicable.stream().filter(rule -> rule.getSecurityIsin() == null).toList();
    if (defaults.size() > 1) {
      throw SetupException.conflict("Multiple effective agreement-default lending fees exist");
    }
    if (defaults.isEmpty()) {
      throw SetupException.missing("No effective lending fee rule exists");
    }
    return defaults.getFirst();
  }
}
