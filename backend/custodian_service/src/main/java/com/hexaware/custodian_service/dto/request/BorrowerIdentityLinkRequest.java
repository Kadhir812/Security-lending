package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BorrowerIdentityLinkRequest(
    @NotBlank @Size(max = 4096) String linkToken) {}
