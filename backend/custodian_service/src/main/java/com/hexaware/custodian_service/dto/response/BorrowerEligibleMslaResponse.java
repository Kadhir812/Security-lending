package com.hexaware.custodian_service.dto.response;

import com.hexaware.custodian_service.dto.request.EligibleSecurities;
import java.time.LocalDate;
import java.util.List;

public record BorrowerEligibleMslaResponse(
    String mslaReference,
    String mandateReference,
    String lenderOrganizationReference,
    String borrowerOrganizationReference,
    String custodianOrganizationReference,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    String baseCurrency,
    List<String> allowedAssetTypes,
    EligibleSecurities eligibleSecurities) {}
