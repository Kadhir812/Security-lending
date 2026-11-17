package com.hexaware.borrower_service.dto.request;

import com.hexaware.borrower_service.enums.BorrowerOrderSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BorrowerOrderSubmission(
    @NotBlank @Size(max = 80) String transactionReference,
    @NotBlank @Pattern(regexp = "[A-Z0-9]{12}") String securityIsin,
    @NotNull BorrowerOrderSide side,
    @NotNull @Positive Long orderQuantity) {}
