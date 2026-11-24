package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record LenderCashMovementRequest(
    @NotNull @DecimalMin("0.01") @Digits(integer = 18, fraction = 2) BigDecimal amount,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,100}") String externalReference,
    @Size(max = 500) String description) {}
