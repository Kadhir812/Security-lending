package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LocateApprovalRequest(@NotNull @Positive Long approvedQuantity) {}
