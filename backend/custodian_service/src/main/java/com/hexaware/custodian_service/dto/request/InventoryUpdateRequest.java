package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InventoryUpdateRequest(
    @NotNull @PositiveOrZero Long totalQuantity, @NotNull Long version) {}
