package com.hexaware.borrower_service.client;

public record BorrowerSecurityReference(
    String isin,
    String symbol,
    String securityName,
    String securityType,
    String currency,
    String status) {
  public BorrowerSecurityReference(
      String isin, String securityType, String currency, String status) {
    this(isin, null, null, securityType, currency, status);
  }
}
