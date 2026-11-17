package com.hexaware.custodian_service.kafka.event;

public record CollateralInquirySubmittedPayload(
    String inquiryId,
    String requestReference,
    String mslaReference,
    String borrowerOrganizationReference,
    String custodianOrganizationReference) {}
