package com.hexaware.borrower_service.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record BorrowerEligibleMslaResponse(
    String mslaReference,
    String mandateReference,
    String lenderOrganizationReference,
    String borrowerOrganizationReference,
    String custodianOrganizationReference,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    String agreementStatus,
    LocalDate mandateEffectiveFrom,
    LocalDate mandateEffectiveTo,
    String mandateStatus,
    String baseCurrency,
    List<String> allowedAssetTypes,
    Map<String, Object> eligibleSecurities) {}
