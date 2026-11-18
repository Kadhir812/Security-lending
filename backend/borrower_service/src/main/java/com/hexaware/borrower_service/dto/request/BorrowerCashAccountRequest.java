package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BorrowerCashAccountRequest(
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}
