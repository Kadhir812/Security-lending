package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LocateSubmission(
    @NotBlank @Size(max = 80) String mslaReference,
    @NotBlank @Pattern(regexp = "[A-Z0-9]{12}") String securityIsin,
    @Positive Long quantity) {}
