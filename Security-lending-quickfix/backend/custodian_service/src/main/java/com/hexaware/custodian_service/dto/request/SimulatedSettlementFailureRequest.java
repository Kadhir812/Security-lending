package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SimulatedSettlementFailureRequest(
    @NotBlank @Size(max = 80) String instructionReference,
    @NotBlank @Size(max = 500) String failureReason) {}
