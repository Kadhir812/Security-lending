package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CalculationScheduleRequest(
    @Min(5) @Max(1440) int intervalMinutes) {}
