package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateRecallRequest(
    @NotBlank @Size(max = 80) String recallReference,
    @NotNull @Positive Long quantity,
    @NotNull Instant dueAt,
    @NotBlank @Size(max = 500) String reason) {}
