package com.hexaware.borrower_service.client;

public record BorrowerSecurityReference(
    String isin,
    String securityType,
    String currency,
    String status) {}
