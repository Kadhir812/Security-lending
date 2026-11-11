package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.Positive;

public record LocateDecisionRequest(@Positive Long approvedQuantity, String reason) {}
