package com.hexaware.custodian_service.dto.response;

public record MslaSetupResponse(
    MandateResponse mandate,
    MslaResponse agreement,
    CreditLimitResponse creditLimit,
    CollateralHaircutResponse cashHaircut,
    LendingFeeResponse lendingFee) {}
