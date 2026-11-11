package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SecuritiesReturnRequest(
    @NotBlank @Size(max = 80) String instructionReference,
    @Size(max = 80) String recallReference,
    @Positive Long quantity) {}
