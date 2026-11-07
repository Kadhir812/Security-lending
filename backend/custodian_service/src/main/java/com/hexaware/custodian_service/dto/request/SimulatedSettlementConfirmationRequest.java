package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SimulatedSettlementConfirmationRequest(
    @NotBlank String instructionReference,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,150}") String confirmationReference) {}
