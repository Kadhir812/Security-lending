package com.hexaware.custodian_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MslaSetupRequest(
    @Valid MandateRequest mandate,
    @Size(max = 80) String existingMandateReference,
    @NotNull @Valid MslaRequest agreement,
    @NotNull @Valid CreditLimitCreateRequest creditLimit,
    @NotNull @PositiveOrZero @Digits(integer = 4, fraction = 6) BigDecimal annualFeePct) {}
