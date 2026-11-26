package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SettlementFailureRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}") String instructionReference,
    @NotBlank @Size(max = 500) String failureReason) {}
