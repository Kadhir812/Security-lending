package com.hexaware.custodian_service.dto.response;

import com.hexaware.custodian_service.dto.request.EligibleSecurities;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.MandateStatus;
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
    AgreementStatus agreementStatus,
    LocalDate mandateEffectiveFrom,
    LocalDate mandateEffectiveTo,
    MandateStatus mandateStatus,
    String baseCurrency,
    List<String> allowedAssetTypes,
    EligibleSecurities eligibleSecurities) {}
