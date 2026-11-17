package com.hexaware.custodian_service.service.fees;

record FeeContractParties(
    Long borrowerId,
    String borrowerReference,
    Long lenderId,
    String lenderReference,
    Long custodianId,
    String custodianReference) {}
