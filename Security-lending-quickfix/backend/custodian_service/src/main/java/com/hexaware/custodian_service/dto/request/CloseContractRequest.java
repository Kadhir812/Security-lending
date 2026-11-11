package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CloseContractRequest(
    @NotBlank @Size(max = 48) @Pattern(regexp = "[A-Za-z0-9._:-]+") String closureReference,
    @NotBlank @Size(max = 500) String reason) {}
