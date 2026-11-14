package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SimulatedCashConfirmationRequest(
    @NotBlank String instructionReference,
    @NotBlank String simulatedConfirmationReference) {}
