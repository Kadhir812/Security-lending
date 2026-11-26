package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record LoanRequestSubmission(
    @NotBlank @Size(max = 80) String mslaReference,
    @NotBlank @Pattern(regexp = "[A-Z0-9]{12}") String securityIsin,
    @NotNull @Positive Long quantity,
    @NotNull LocalDate requestedSettlementDate,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String settlementCurrency,
    @Size(max = 80) String locateReference) {}
