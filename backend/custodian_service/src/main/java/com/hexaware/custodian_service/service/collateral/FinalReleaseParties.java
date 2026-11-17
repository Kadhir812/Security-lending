package com.hexaware.custodian_service.service.collateral;

public record FinalReleaseParties(
    Long borrowerId,
    String borrowerReference,
    Long custodianId,
    String custodianReference) {}
