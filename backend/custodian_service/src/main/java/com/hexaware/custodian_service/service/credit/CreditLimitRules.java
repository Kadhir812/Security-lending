package com.hexaware.custodian_service.service.credit;

import java.math.BigDecimal;

public final class CreditLimitRules {

  private CreditLimitRules() {}

  public static BigDecimal available(
      BigDecimal limit, BigDecimal used, BigDecimal reserved) {
    return limit.subtract(used).subtract(reserved);
  }
}
