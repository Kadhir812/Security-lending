package com.hexaware.custodian_service.service.inventory;

import com.hexaware.custodian_service.exception.SetupException;

public final class InventoryQuantityRules {

  private InventoryQuantityRules() {}

  public static long available(long total, long onLoan, long reserved) {
    return total - onLoan - reserved;
  }

  public static void requireTotalCoversEncumbrances(long total, long onLoan, long reserved) {
    if (total < onLoan + reserved) {
      throw SetupException.conflict(
          "Total quantity cannot be lower than reserved plus on-loan quantity");
    }
  }
}
