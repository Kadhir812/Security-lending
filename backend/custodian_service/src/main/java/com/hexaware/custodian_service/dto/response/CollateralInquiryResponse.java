package com.hexaware.custodian_service.dto.response;

import java.time.Instant;

public record CollateralInquiryResponse(
    String inquiryId,
    String requestReference,
    String mslaReference,
    String borrowerOrganizationReference,
    String custodianOrganizationReference,
    Instant receivedAt,
    String processingStatus,
    String reportEventId,
    String reportStatus,
    Instant reportPublishedAt) {}
