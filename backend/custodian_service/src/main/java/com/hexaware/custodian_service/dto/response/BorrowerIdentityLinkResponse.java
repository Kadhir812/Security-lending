package com.hexaware.custodian_service.dto.response;

public record BorrowerIdentityLinkResponse(
    String status,
    String organizationReference,
    String organizationName,
    String username,
    String email) {}
