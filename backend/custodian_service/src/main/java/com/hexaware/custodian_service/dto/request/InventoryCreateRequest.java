package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InventoryCreateRequest(
    @NotBlank String lenderOrganizationReference,
    @NotBlank String securityIsin,
    @NotNull @PositiveOrZero Long totalQuantity) {}
